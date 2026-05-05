package com.example.redmineagent.infrastructure.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * `GET /api/sync/status` のレスポンス DTO。
 */
@Schema(description = "同期状態のスナップショット")
data class SyncStatusDto(
    @field:Schema(description = "ソース名 ('redmine' 固定)", example = "redmine")
    val source: String,
    @field:Schema(description = "直近の同期開始時刻", nullable = true)
    val lastSyncStartedAt: Instant?,
    @field:Schema(description = "直近の同期完了時刻", nullable = true)
    val lastSyncFinishedAt: Instant?,
    @field:Schema(description = "直近 Reconcile 完了時刻", nullable = true)
    val lastFullReconcileAt: Instant?,
    @field:Schema(description = "直近のエラーメッセージ", nullable = true)
    val lastError: String?,
    @field:Schema(description = "Qdrant 上のユニーク ticket_id 数", example = "1247")
    val ticketsTotal: Int,
    @field:Schema(description = "現在同期実行中か", example = "false")
    val currentlyRunning: Boolean,
    @field:Schema(description = "実行中の run_id", nullable = true)
    val currentRunId: Long?,
)
