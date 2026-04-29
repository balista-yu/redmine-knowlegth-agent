package com.example.redmineagent.infrastructure.web.dto

import java.time.Instant

/**
 * `GET /api/sync/runs` 各 item の DTO (詳細: docs/03-api-spec.md §5)。
 */
data class SyncRunDto(
    val id: Long,
    val kind: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val status: String,
    val ticketsFetched: Int,
    val chunksUpserted: Int,
    val chunksSkipped: Int,
    val ticketsDeleted: Int,
    val errorMessage: String?,
)

/** `GET /api/sync/runs` のラッパー。 */
data class SyncRunsResponseDto(
    val items: List<SyncRunDto>,
    val total: Int,
)
