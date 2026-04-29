package com.example.redmineagent.infrastructure.web.dto

import java.time.Instant

/**
 * `GET /api/sync/status` のレスポンス DTO (詳細: docs/03-api-spec.md §4)。
 */
data class SyncStatusDto(
    val source: String,
    val lastSyncStartedAt: Instant?,
    val lastSyncFinishedAt: Instant?,
    val lastFullReconcileAt: Instant?,
    val lastError: String?,
    val ticketsTotal: Int,
    val currentlyRunning: Boolean,
    val currentRunId: Long?,
)
