package com.example.redmineagent.infrastructure.agent.tool

import com.example.redmineagent.domain.gateway.EmbeddingService
import com.example.redmineagent.domain.model.ChunkType
import com.example.redmineagent.domain.model.ScoredChunk
import com.example.redmineagent.domain.model.SearchFilter
import com.example.redmineagent.domain.model.TicketChunk
import com.example.redmineagent.domain.model.TicketMetadata
import com.example.redmineagent.domain.repository.TicketChunkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * `RagSearchTool` の単体テスト (docs/02-tasks.md T-2-2 DoD)。
 *
 * 検証ポイント:
 *  - 同 ticket_id の複数 chunk hit → 1 件にまとめ、score は最大値
 *  - filter null と filter 指定の両方で正しく Embedding + Repository が呼ばれる
 */
class RagSearchToolTest :
    FunSpec({
        val embeddingService = mockk<EmbeddingService>()
        val ticketChunkRepository = mockk<TicketChunkRepository>()
        val tool = RagSearchTool(embeddingService, ticketChunkRepository)

        fun scored(
            ticketId: Int,
            content: String,
            score: Float,
        ): ScoredChunk =
            ScoredChunk(
                chunk =
                    TicketChunk(
                        ticketId = ticketId,
                        chunkType = ChunkType.DESCRIPTION,
                        chunkIndex = 0,
                        subIndex = 0,
                        content = content,
                        contentHash = "hash-$ticketId-${content.hashCode()}",
                    ),
                metadata =
                    TicketMetadata(
                        subject = "subject for $ticketId",
                        url = "http://redmine.example/issues/$ticketId",
                        projectName = "proj",
                        status = "New",
                        tracker = "Bug",
                    ),
                score = score,
            )

        test("同 ticket_id の複数 chunk hit は 1 件に集約され score は最大値") {
            val vector = floatArrayOf(0.1f, 0.2f, 0.3f)
            coEvery { embeddingService.embed("how to fix tls") } returns vector
            coEvery {
                ticketChunkRepository.search(any(), 5, SearchFilter.NONE)
            } returns
                listOf(
                    scored(ticketId = 100, content = "low score chunk", score = 0.4f),
                    scored(ticketId = 100, content = "best chunk content", score = 0.9f),
                    scored(ticketId = 200, content = "another ticket", score = 0.6f),
                )

            val raw = tool.execute(RagSearchTool.RagSearchArgs(query = "how to fix tls"))

            val items = Json.parseToJsonElement(raw).jsonArray
            items.size shouldBe 2
            // 最大スコアの chunk が代表として採用される
            val first = items[0].jsonObject
            first["ticketId"]?.jsonPrimitive?.intOrNull shouldBe 100
            first["score"]?.jsonPrimitive?.contentOrNull shouldBe "0.9"
            first["snippet"]?.jsonPrimitive?.contentOrNull shouldContain "best chunk"
            // ticket 200 が次点で出る
            items[1].jsonObject["ticketId"]?.jsonPrimitive?.intOrNull shouldBe 200
        }

        test("filter 指定なしなら SearchFilter NONE が repository に渡る") {
            val filterSlot = slot<SearchFilter>()
            coEvery { embeddingService.embed(any()) } returns floatArrayOf(1.0f)
            coEvery {
                ticketChunkRepository.search(any(), any(), capture(filterSlot))
            } returns emptyList()

            tool.execute(RagSearchTool.RagSearchArgs(query = "x"))

            filterSlot.captured shouldBe SearchFilter.NONE
            coVerify(exactly = 1) { embeddingService.embed("x") }
        }

        test("filter projectId と statusFilter が指定されると SearchFilter に反映される") {
            val filterSlot = slot<SearchFilter>()
            coEvery { embeddingService.embed(any()) } returns floatArrayOf(1.0f)
            coEvery {
                ticketChunkRepository.search(any(), any(), capture(filterSlot))
            } returns emptyList()

            tool.execute(
                RagSearchTool.RagSearchArgs(
                    query = "x",
                    projectId = 7,
                    statusFilter = "Closed",
                ),
            )

            filterSlot.captured.projectId shouldBe 7
            filterSlot.captured.status shouldBe "Closed"
            filterSlot.captured.tracker shouldBe null
        }

        test("limit は 1〜20 にクランプされ repository へ渡る") {
            coEvery { embeddingService.embed(any()) } returns floatArrayOf(1.0f)
            val limitSlot = slot<Int>()
            coEvery {
                ticketChunkRepository.search(any(), capture(limitSlot), any())
            } returns emptyList()

            tool.execute(RagSearchTool.RagSearchArgs(query = "x", limit = 999))

            limitSlot.captured shouldBe 20
        }
    })
