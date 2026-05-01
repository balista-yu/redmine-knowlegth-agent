package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import java.time.Instant

class SyncRunTest :
    FunSpec({
        fun runWith(
            ticketsFetched: Int = 0,
            chunksUpserted: Int = 0,
            chunksSkipped: Int = 0,
            ticketsDeleted: Int = 0,
            startedAt: Instant = Instant.parse("2026-04-29T09:00:00Z"),
            finishedAt: Instant? = null,
        ) = SyncRun(
            id = null,
            kind = SyncRunKind.INCREMENTAL,
            startedAt = startedAt,
            finishedAt = finishedAt,
            ticketsFetched = ticketsFetched,
            chunksUpserted = chunksUpserted,
            chunksSkipped = chunksSkipped,
            ticketsDeleted = ticketsDeleted,
            status = SyncRunStatus.RUNNING,
            errorMessage = null,
        )

        test("tickets_fetched が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { runWith(ticketsFetched = -1) }
        }

        test("chunks_upserted が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { runWith(chunksUpserted = -1) }
        }

        test("chunks_skipped が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { runWith(chunksSkipped = -1) }
        }

        test("tickets_deleted が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { runWith(ticketsDeleted = -1) }
        }

        test("finishedAt が startedAt より前なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                runWith(
                    startedAt = Instant.parse("2026-04-29T10:00:00Z"),
                    finishedAt = Instant.parse("2026-04-29T09:59:59Z"),
                )
            }
        }
    })
