package com.example.redmineagent.infrastructure.web.dto

import java.time.Instant

/**
 * `POST /api/sync` / `POST /api/reconcile` 200 OK のレスポンス DTO
 * (詳細: docs/03-api-spec.md §2, §3)。
 *
 * 注: 本フェーズの ApplicationService は同期実行 (execute() 完走後にレスポンス返却) のため、
 * `status` は実際には `success` / `failed` のいずれかになる。仕様例の `running` は将来の
 * 非同期 dispatch 用に予約されているフィールド値で、現在は最終状態をそのまま返す。
 */
data class SyncStartedDto(
    val runId: Long,
    val kind: String,
    val startedAt: Instant,
    val status: String,
)
