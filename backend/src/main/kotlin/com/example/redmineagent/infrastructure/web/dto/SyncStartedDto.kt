package com.example.redmineagent.infrastructure.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/**
 * `POST /api/sync` / `POST /api/reconcile` 200 OK のレスポンス DTO。
 *
 * 注: 本フェーズの ApplicationService は同期実行 (execute() 完走後にレスポンス返却) のため、
 * `status` は実際には `success` / `failed` のいずれかになる。仕様例の `running` は将来の
 * 非同期 dispatch 用に予約されているフィールド値で、現在は最終状態をそのまま返す。
 */
@Schema(description = "同期 / Reconcile run 開始レスポンス")
data class SyncStartedDto(
    @field:Schema(description = "sync_run の ID", example = "42")
    val runId: Long,
    @field:Schema(description = "種別", example = "incremental", allowableValues = ["incremental", "full", "reconcile"])
    val kind: String,
    @field:Schema(description = "開始時刻 (ISO8601)", example = "2024-04-26T10:00:00Z")
    val startedAt: Instant,
    @field:Schema(description = "状態", example = "success", allowableValues = ["running", "success", "failed"])
    val status: String,
)
