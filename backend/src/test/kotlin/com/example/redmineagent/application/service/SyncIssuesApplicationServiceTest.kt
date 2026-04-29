package com.example.redmineagent.application.service

import com.example.redmineagent.application.exception.EmbeddingTooLongException
import com.example.redmineagent.application.exception.SyncAlreadyRunningException
import com.example.redmineagent.domain.gateway.EmbeddingService
import com.example.redmineagent.domain.gateway.RedmineGateway
import com.example.redmineagent.domain.model.ChunkType
import com.example.redmineagent.domain.model.IssuePage
import com.example.redmineagent.domain.model.SyncMode
import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import com.example.redmineagent.domain.model.SyncState
import com.example.redmineagent.domain.model.TicketChunk
import com.example.redmineagent.domain.model.TicketChunkVector
import com.example.redmineagent.domain.repository.SyncStateRepository
import com.example.redmineagent.domain.repository.TicketChunkRepository
import com.example.redmineagent.domain.service.ChunkBuilder
import com.example.redmineagent.testdata.IssueFixture
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * `SyncIssuesApplicationService` の単体テスト。docs/02-tasks.md T-1-10 テスト要件 6 ケース。
 * Spring を立てず MockK で全 Domain interface をモック化する (.claude/rules/testing.md)。
 */
class SyncIssuesApplicationServiceTest {
    private val redmineGateway = mockk<RedmineGateway>()
    private val ticketChunkRepository = mockk<TicketChunkRepository>()
    private val embeddingService = mockk<EmbeddingService>()
    private val syncStateRepository = mockk<SyncStateRepository>()

    private val service =
        SyncIssuesApplicationService(
            redmineGateway = redmineGateway,
            ticketChunkRepository = ticketChunkRepository,
            embeddingService = embeddingService,
            syncStateRepository = syncStateRepository,
        )

    @Test
    fun `正常系 - Issue 3 件全新規なら全チャンクが embed と upsert される`() =
        runTest {
            val issues =
                listOf(
                    IssueFixture.issue(ticketId = 1, description = "alpha"),
                    IssueFixture.issue(ticketId = 2, description = "beta"),
                    IssueFixture.issue(ticketId = 3, description = "gamma"),
                )
            stubInitialState()
            coEvery { redmineGateway.listIssuesUpdatedSince(any(), 0, any()) } returns
                IssuePage(issues = issues, totalCount = 3, offset = 0, limit = 100)
            coEvery { ticketChunkRepository.findByTicketId(any()) } returns emptyList()
            coEvery { embeddingService.embed(any()) } returns dummyVector()
            coEvery { ticketChunkRepository.upsert(any()) } just Runs
            coEvery { ticketChunkRepository.deleteOrphanChunks(any(), any()) } returns 0
            val finishedSlot = slot<SyncRun>()
            coEvery { syncStateRepository.completeRun(capture(finishedSlot)) } answers {
                finishedSlot.captured
            }

            val result = service.execute(SyncMode.INCREMENTAL)

            result.status shouldBe SyncRunStatus.SUCCESS
            result.ticketsFetched shouldBe 3
            result.chunksUpserted shouldBe 3
            result.chunksSkipped shouldBe 0
            coVerify(exactly = 3) { embeddingService.embed(any()) }
            coVerify(exactly = 3) { ticketChunkRepository.upsert(any()) }
            coVerify { syncStateRepository.updateLastSyncStartedAt(any()) }
        }

    @Test
    fun `差分なし - 既存と contentHash 一致なら chunksSkipped が増え embed と upsert は呼ばれない`() =
        runTest {
            val issue = IssueFixture.issue(ticketId = 7, description = "unchanged content")
            // ChunkBuilder の生成と完全一致するチャンクを既存として返す → 差分なし
            val existingChunks: List<TicketChunk> = ChunkBuilder.build(issue)
            stubInitialState()
            coEvery { redmineGateway.listIssuesUpdatedSince(any(), 0, any()) } returns
                IssuePage(issues = listOf(issue), totalCount = 1, offset = 0, limit = 100)
            coEvery { ticketChunkRepository.findByTicketId(7) } returns existingChunks
            coEvery { ticketChunkRepository.deleteOrphanChunks(7, any()) } returns 0
            val finishedSlot = slot<SyncRun>()
            coEvery { syncStateRepository.completeRun(capture(finishedSlot)) } answers {
                finishedSlot.captured
            }

            val result = service.execute()

            result.chunksSkipped shouldBe existingChunks.size
            result.chunksUpserted shouldBe 0
            coVerify(exactly = 0) { embeddingService.embed(any()) }
            coVerify(exactly = 0) { ticketChunkRepository.upsert(any()) }
        }

    @Test
    fun `journal 縮小 - 既存より少ないチャンクなら deleteOrphanChunks の戻り値が ticketsDeleted に反映される`() =
        runTest {
            // 現在は description のみ (1 chunk)
            val issue = IssueFixture.issue(ticketId = 11, description = "current desc")
            val currentChunks = ChunkBuilder.build(issue)
            // 既存には description + journal chunk が残っているが、新チャンクには含まれない
            val obsoleteJournal =
                TicketChunk(
                    ticketId = 11,
                    chunkType = ChunkType.JOURNAL,
                    chunkIndex = 1,
                    subIndex = 0,
                    content = "old journal note",
                    contentHash = "obsolete-hash",
                )
            stubInitialState()
            coEvery { redmineGateway.listIssuesUpdatedSince(any(), 0, any()) } returns
                IssuePage(issues = listOf(issue), totalCount = 1, offset = 0, limit = 100)
            coEvery { ticketChunkRepository.findByTicketId(11) } returns
                currentChunks + obsoleteJournal
            coEvery { embeddingService.embed(any()) } returns dummyVector()
            coEvery { ticketChunkRepository.upsert(any()) } just Runs
            coEvery { ticketChunkRepository.deleteOrphanChunks(11, currentChunks) } returns 1
            coEvery { syncStateRepository.completeRun(any()) } answers { firstArg() }

            val result = service.execute()

            result.ticketsDeleted shouldBe 1
            coVerify(exactly = 1) { ticketChunkRepository.deleteOrphanChunks(11, currentChunks) }
        }

    @Test
    fun `EmbeddingTooLongException - 半分分割で再試行し最終的に成功する`() =
        runTest {
            val issue = IssueFixture.issue(ticketId = 21, description = "long content x 2")
            val originalChunks = ChunkBuilder.build(issue)
            val originalContent = originalChunks.single().content
            val firstHalf = originalContent.substring(0, originalContent.length / 2)
            val secondHalf = originalContent.substring(originalContent.length / 2)

            stubInitialState()
            coEvery { redmineGateway.listIssuesUpdatedSince(any(), 0, any()) } returns
                IssuePage(issues = listOf(issue), totalCount = 1, offset = 0, limit = 100)
            coEvery { ticketChunkRepository.findByTicketId(21) } returns emptyList()
            // 元 content では超過、各 half では成功
            coEvery { embeddingService.embed(originalContent) } throws
                EmbeddingTooLongException("input too long")
            coEvery { embeddingService.embed(firstHalf) } returns dummyVector()
            coEvery { embeddingService.embed(secondHalf) } returns dummyVector()
            val upsertSlot = slot<List<TicketChunkVector>>()
            coEvery { ticketChunkRepository.upsert(capture(upsertSlot)) } just Runs
            coEvery { ticketChunkRepository.deleteOrphanChunks(21, any()) } returns 0
            coEvery { syncStateRepository.completeRun(any()) } answers { firstArg() }

            val result = service.execute()

            // 元の 1 chunk が 2 chunk に分裂して upsert される
            upsertSlot.captured.size shouldBe 2
            result.chunksUpserted shouldBe 2
            result.status shouldBe SyncRunStatus.SUCCESS
            coVerify(exactly = 1) { embeddingService.embed(originalContent) }
            coVerify(exactly = 1) { embeddingService.embed(firstHalf) }
            coVerify(exactly = 1) { embeddingService.embed(secondHalf) }
        }

    @Test
    fun `Redmine 5xx 失敗 - failRun で last_error が更新され例外が伝播する`() =
        runTest {
            stubInitialState()
            coEvery { redmineGateway.listIssuesUpdatedSince(any(), any(), any()) } throws
                RuntimeException("Redmine returned 503 after retries")
            val failedSlot = slot<SyncRun>()
            coEvery { syncStateRepository.failRun(capture(failedSlot)) } answers { failedSlot.captured }
            coEvery { syncStateRepository.updateLastError(any()) } just Runs

            shouldThrow<RuntimeException> { service.execute() }

            failedSlot.captured.status shouldBe SyncRunStatus.FAILED
            failedSlot.captured.errorMessage shouldBe "Redmine returned 503 after retries"
            coVerify { syncStateRepository.updateLastError("Redmine returned 503 after retries") }
            coVerify(exactly = 0) { syncStateRepository.completeRun(any()) }
            coVerify(exactly = 0) { syncStateRepository.updateLastSyncStartedAt(any()) }
        }

    @Test
    fun `走行中の二重実行 - SyncAlreadyRunningException が即時投げられる`() =
        runTest {
            // 1 回目を gate で待機させ、走行中状態を作る
            val gate = CompletableDeferred<Unit>()
            coEvery { syncStateRepository.load() } coAnswers {
                gate.await()
                SyncState(
                    source = "redmine",
                    lastSyncStartedAt = null,
                    lastSyncFinishedAt = null,
                    lastFullReconcileAt = null,
                    lastError = null,
                    ticketsTotal = 0,
                )
            }
            coEvery { syncStateRepository.startRun(any(), any()) } answers {
                SyncRun(
                    id = 1L,
                    kind = SyncRunKind.INCREMENTAL,
                    startedAt = Instant.now(),
                    finishedAt = null,
                    ticketsFetched = 0,
                    chunksUpserted = 0,
                    chunksSkipped = 0,
                    ticketsDeleted = 0,
                    status = SyncRunStatus.RUNNING,
                    errorMessage = null,
                )
            }
            coEvery { redmineGateway.listIssuesUpdatedSince(any(), any(), any()) } returns
                IssuePage(issues = emptyList(), totalCount = 0, offset = 0, limit = 100)
            coEvery { syncStateRepository.completeRun(any()) } answers { firstArg() }
            coEvery { syncStateRepository.updateLastSyncStartedAt(any()) } just Runs
            coEvery { syncStateRepository.updateLastError(any()) } just Runs

            val first = async { service.execute() }
            // 1 回目が AtomicBoolean を取得するまで進める
            yield()

            shouldThrow<SyncAlreadyRunningException> { service.execute() }

            // 1 回目の完走を許可し、リーク防止
            gate.complete(Unit)
            first.await()
        }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private fun stubInitialState() {
        coEvery { syncStateRepository.load() } returns
            SyncState(
                source = "redmine",
                lastSyncStartedAt = null,
                lastSyncFinishedAt = null,
                lastFullReconcileAt = null,
                lastError = null,
                ticketsTotal = 0,
            )
        coEvery { syncStateRepository.startRun(any(), any()) } answers {
            SyncRun(
                id = 1L,
                kind = firstArg(),
                startedAt = secondArg(),
                finishedAt = null,
                ticketsFetched = 0,
                chunksUpserted = 0,
                chunksSkipped = 0,
                ticketsDeleted = 0,
                status = SyncRunStatus.RUNNING,
                errorMessage = null,
            )
        }
        coEvery { syncStateRepository.updateLastSyncStartedAt(any()) } just Runs
        coEvery { syncStateRepository.updateLastError(any()) } just Runs
    }

    private fun dummyVector(): FloatArray = floatArrayOf(0.1f, 0.2f, 0.3f)
}
