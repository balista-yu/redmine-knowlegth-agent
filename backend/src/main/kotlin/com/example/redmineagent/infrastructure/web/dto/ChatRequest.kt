package com.example.redmineagent.infrastructure.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * `POST /api/chat` のリクエスト DTO。
 *
 * @property message ユーザーからの質問。1〜4000 文字
 * @property conversationId 会話単位識別子。省略時はサーバ側で UUID 採番
 */
@Schema(description = "POST /api/chat のリクエスト")
data class ChatRequest(
    @field:Schema(
        description = "ユーザーからの質問 (1〜${MESSAGE_MAX_LENGTH} 文字)",
        example = "SSL 証明書のエラーで過去に対応した事例ある?",
        requiredMode = Schema.RequiredMode.REQUIRED,
    )
    @field:NotBlank(message = "message must not be blank")
    @field:Size(min = MESSAGE_MIN_LENGTH, max = MESSAGE_MAX_LENGTH, message = "message length must be 1..$MESSAGE_MAX_LENGTH")
    val message: String = "",
    @field:Schema(
        description = "会話単位識別子。省略時はサーバ側で UUID v4 を採番する",
        example = "c-2024-04-26-001",
        nullable = true,
    )
    val conversationId: String? = null,
) {
    companion object {
        const val MESSAGE_MIN_LENGTH = 1
        const val MESSAGE_MAX_LENGTH = 4000
    }
}
