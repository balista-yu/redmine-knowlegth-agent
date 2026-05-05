package com.example.redmineagent.infrastructure.web.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 非 SSE エンドポイントの汎用エラーレスポンス。
 *
 * `details` / `currentRunId` は欠損可。`@JsonInclude(NON_NULL)` で出力時に null を省く。
 */
@Schema(description = "汎用エラーレスポンス (非 SSE エンドポイント共通)")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiErrorDto(
    @field:Schema(
        description = "エラーコード",
        example = "SYNC_ALREADY_RUNNING",
        allowableValues = [
            "INVALID_REQUEST",
            "NOT_FOUND",
            "SYNC_ALREADY_RUNNING",
            "OLLAMA_UNAVAILABLE",
            "QDRANT_UNAVAILABLE",
            "REDMINE_UNAVAILABLE",
            "INTERNAL",
        ],
    )
    val code: String,
    @field:Schema(description = "ユーザー向けメッセージ", example = "同期が既に実行中です")
    val message: String,
    @field:Schema(description = "追加情報 (任意)", nullable = true)
    val details: Map<String, Any?>? = null,
    @field:Schema(description = "実行中の sync_run.id (SYNC_ALREADY_RUNNING のときのみ)", nullable = true)
    val currentRunId: Long? = null,
)
