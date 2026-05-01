package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class ScoredChunkTest {
    private val baseChunk =
        TicketChunk(
            ticketId = 1,
            chunkType = ChunkType.DESCRIPTION,
            chunkIndex = 0,
            subIndex = 0,
            content = "body",
            contentHash = "abc",
        )
    private val baseMetadata =
        TicketMetadata(
            subject = "subject",
            url = "http://redmine.example/issues/1",
            projectName = "proj",
            status = "New",
            tracker = "Bug",
        )

    @Test
    fun `score が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            ScoredChunk(chunk = baseChunk, metadata = baseMetadata, score = -0.01f)
        }
    }
}
