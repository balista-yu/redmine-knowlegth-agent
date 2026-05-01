package com.example.redmineagent.infrastructure.web.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * `POST /api/chat` のリクエスト DTO (詳細: docs/03-api-spec.md §1)。
 *
 * @property message ユーザーからの質問。1〜4000 文字
 * @property conversationId 会話単位識別子。省略時はサーバ側で UUID 採番
 */
data class ChatRequest(
    @field:NotBlank(message = "message must not be blank")
    @field:Size(min = MESSAGE_MIN_LENGTH, max = MESSAGE_MAX_LENGTH, message = "message length must be 1..$MESSAGE_MAX_LENGTH")
    val message: String = "",
    val conversationId: String? = null,
) {
    companion object {
        const val MESSAGE_MIN_LENGTH = 1
        const val MESSAGE_MAX_LENGTH = 4000
    }
}
