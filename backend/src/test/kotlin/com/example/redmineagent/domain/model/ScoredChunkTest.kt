package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class ScoredChunkTest :
    FunSpec({
        val baseChunk =
            TicketChunk(
                ticketId = 1,
                chunkType = ChunkType.DESCRIPTION,
                chunkIndex = 0,
                subIndex = 0,
                content = "body",
                contentHash = "abc",
            )
        val baseMetadata =
            TicketMetadata(
                subject = "subject",
                url = "http://redmine.example/issues/1",
                projectName = "proj",
                status = "New",
                tracker = "Bug",
            )

        test("score が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                ScoredChunk(chunk = baseChunk, metadata = baseMetadata, score = -0.01f)
            }
        }
    })
