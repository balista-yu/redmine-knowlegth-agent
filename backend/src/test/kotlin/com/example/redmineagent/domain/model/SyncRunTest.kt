package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test
import java.time.Instant

class SyncRunTest {
    @Test
    fun `tickets_fetched が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            runWith(ticketsFetched = -1)
        }
    }

    @Test
    fun `chunks_upserted が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            runWith(chunksUpserted = -1)
        }
    }

    @Test
    fun `chunks_skipped が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            runWith(chunksSkipped = -1)
        }
    }

    @Test
    fun `tickets_deleted が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            runWith(ticketsDeleted = -1)
        }
    }

    @Test
    fun `finishedAt が startedAt より前なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            runWith(
                startedAt = Instant.parse("2026-04-29T10:00:00Z"),
                finishedAt = Instant.parse("2026-04-29T09:59:59Z"),
            )
        }
    }

    private fun runWith(
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
}
