package com.example.redmineagent.infrastructure.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * `GET /api/sync/runs` 各 item の DTO。
 */
@Schema(description = "sync_run 1 件分の履歴エントリ")
data class SyncRunDto(
    @field:Schema(description = "sync_run の ID", example = "42")
    val id: Long,
    @field:Schema(description = "種別", example = "incremental", allowableValues = ["incremental", "full", "reconcile"])
    val kind: String,
    @field:Schema(description = "開始時刻 (ISO8601)")
    val startedAt: Instant,
    @field:Schema(description = "終了時刻 (ISO8601)", nullable = true)
    val finishedAt: Instant?,
    @field:Schema(description = "状態", example = "success", allowableValues = ["success", "failed"])
    val status: String,
    @field:Schema(description = "Redmine から取得した issue 件数", example = "12")
    val ticketsFetched: Int,
    @field:Schema(description = "Qdrant に upsert した chunk 件数", example = "18")
    val chunksUpserted: Int,
    @field:Schema(description = "差分なしで skip した chunk 件数", example = "6")
    val chunksSkipped: Int,
    @field:Schema(description = "Reconcile で削除した ticket 件数", example = "0")
    val ticketsDeleted: Int,
    @field:Schema(description = "失敗時のエラーメッセージ", nullable = true)
    val errorMessage: String?,
)

/** `GET /api/sync/runs` のラッパー。 */
@Schema(description = "sync_run 履歴のラッパーレスポンス")
data class SyncRunsResponseDto(
    @field:Schema(description = "履歴エントリの配列 (新しい順)")
    val items: List<SyncRunDto>,
    @field:Schema(description = "items の件数", example = "20")
    val total: Int,
)
