package com.example.redmineagent.infrastructure.web.dto

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * 非 SSE エンドポイントの汎用エラーレスポンス (docs/03-api-spec.md §6)。
 *
 * `details` / `currentRunId` は欠損可。`@JsonInclude(NON_NULL)` で出力時に null を省く。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiErrorDto(
    val code: String,
    val message: String,
    val details: Map<String, Any?>? = null,
    val currentRunId: Long? = null,
)
