package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class TicketChunkTest :
    FunSpec({
        fun chunkWith(
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

        test("ticketId が 0 以下なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { chunkWith(ticketId = 0) }
        }

        test("content が blank なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { chunkWith(content = "   ") }
        }

        test("chunkIndex が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { chunkWith(chunkIndex = -1) }
        }

        test("subIndex が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { chunkWith(subIndex = -1) }
        }

        test("contentHash が blank なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { chunkWith(contentHash = "") }
        }
    })
