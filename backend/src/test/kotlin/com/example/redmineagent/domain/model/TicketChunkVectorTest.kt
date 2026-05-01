package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TicketChunkVectorTest :
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

        test("vector が空なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                TicketChunkVector(chunk = baseChunk, vector = floatArrayOf(), metadata = baseMetadata)
            }
        }

        test("通常の vector は dimension が反映される") {
            val v = TicketChunkVector(chunk = baseChunk, vector = FloatArray(768) { 0f }, metadata = baseMetadata)
            v.dimension shouldBe 768
        }
    })
