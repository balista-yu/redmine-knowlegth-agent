package com.example.redmineagent.infrastructure.external.qdrant

import com.example.redmineagent.domain.model.ChunkType
import com.example.redmineagent.domain.model.TicketChunk
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test

/**
 * UUID v5 (namespace + name) で同一入力 → 同一 UUID、異なる position → 異なる UUID
 * を担保することを単体検証。
 */
class PointIdGeneratorTest {
    @Test
    fun `同じ ticketId と chunkIndex と subIndex なら同一 UUID`() {
        val a = PointIdGenerator.pointId(ticketId = 42, chunkIndex = 0, subIndex = 0)
        val b = PointIdGenerator.pointId(ticketId = 42, chunkIndex = 0, subIndex = 0)
        a shouldBe b
    }

    @Test
    fun `chunk から生成しても position が同じなら同じ UUID`() {
        val chunk =
            TicketChunk(
                ticketId = 42,
                chunkType = ChunkType.DESCRIPTION,
                chunkIndex = 0,
                subIndex = 0,
                content = "anything",
                contentHash = "any-hash",
            )
        val fromChunk = PointIdGenerator.pointId(chunk)
        val fromInts = PointIdGenerator.pointId(ticketId = 42, chunkIndex = 0, subIndex = 0)
        fromChunk shouldBe fromInts
    }

    @Test
    fun `position が違えば UUID も違う`() {
        val base = PointIdGenerator.pointId(ticketId = 42, chunkIndex = 0, subIndex = 0)
        PointIdGenerator.pointId(ticketId = 43, chunkIndex = 0, subIndex = 0) shouldNotBe base
        PointIdGenerator.pointId(ticketId = 42, chunkIndex = 1, subIndex = 0) shouldNotBe base
        PointIdGenerator.pointId(ticketId = 42, chunkIndex = 0, subIndex = 1) shouldNotBe base
    }

    @Test
    fun `生成された UUID は version 5 で variant=RFC4122`() {
        val uuid = PointIdGenerator.pointId(ticketId = 1, chunkIndex = 0, subIndex = 0)
        uuid.version() shouldBe 5
        uuid.variant() shouldBe 2 // RFC 4122
    }
}
