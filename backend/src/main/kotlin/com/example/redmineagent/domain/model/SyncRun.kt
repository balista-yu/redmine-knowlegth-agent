package com.example.redmineagent.domain.model

import java.time.Instant

/**
 * 同期 1 回分の実行履歴。`sync_run` テーブルの 1 行に対応 (詳細: docs/01-design.md §4.2)。
 *
 * `id` は永続化前は null、永続化後は DB 採番値。
 */
data class SyncRun(
    val id: Long?,
    val kind: SyncRunKind,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val ticketsFetched: Int,
    val chunksUpserted: Int,
    val chunksSkipped: Int,
    val ticketsDeleted: Int,
    val status: SyncRunStatus,
    val errorMessage: String?,
) {
    init {
        require(ticketsFetched >= 0) { "ticketsFetched must be non-negative: $ticketsFetched" }
        require(chunksUpserted >= 0) { "chunksUpserted must be non-negative: $chunksUpserted" }
        require(chunksSkipped >= 0) { "chunksSkipped must be non-negative: $chunksSkipped" }
        require(ticketsDeleted >= 0) { "ticketsDeleted must be non-negative: $ticketsDeleted" }
        if (finishedAt != null) {
            require(!finishedAt.isBefore(startedAt)) {
                "finishedAt ($finishedAt) must be on or after startedAt ($startedAt)"
            }
        }
    }
}
