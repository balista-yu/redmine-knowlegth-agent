package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class TicketChunkTest {
    @Test
    fun `ticketId が 0 以下なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            chunkWith(ticketId = 0)
        }
    }

    @Test
    fun `content が blank なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            chunkWith(content = "   ")
        }
    }

    @Test
    fun `chunkIndex が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            chunkWith(chunkIndex = -1)
        }
    }

    @Test
    fun `subIndex が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            chunkWith(subIndex = -1)
        }
    }

    @Test
    fun `contentHash が blank なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            chunkWith(contentHash = "")
        }
    }

    private fun chunkWith(
        ticketId: Int = 1,
        chunkType: ChunkType = ChunkType.DESCRIPTION,
        chunkIndex: Int = 0,
        subIndex: Int = 0,
        content: String = "body",
        contentHash: String = "abc",
    ) = TicketChunk(
        ticketId = ticketId,
        chunkType = chunkType,
        chunkIndex = chunkIndex,
        subIndex = subIndex,
        content = content,
        contentHash = contentHash,
    )
}
