package com.example.redmineagent.domain.model

import java.time.Instant

/**
 * 同期 source (本フェーズでは "redmine" 固定) 1 件の直近状態。
 * `sync_state` テーブルの 1 行に対応 (詳細: docs/01-design.md §4.2)。
 */
data class SyncState(
    val source: String,
    val lastSyncStartedAt: Instant?,
    val lastSyncFinishedAt: Instant?,
    val lastFullReconcileAt: Instant?,
    val lastError: String?,
    val ticketsTotal: Int,
) {
    init {
        require(source.isNotBlank()) { "source must not be blank" }
        require(ticketsTotal >= 0) { "ticketsTotal must be non-negative: $ticketsTotal" }
    }
}
