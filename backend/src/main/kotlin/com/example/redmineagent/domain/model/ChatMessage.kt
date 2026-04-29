package com.example.redmineagent.domain.model

/**
 * チャット会話の 1 メッセージ。
 * `conversationId` ごとに In-Memory 保持される会話履歴の要素。
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String,
) {
    init {
        require(content.isNotBlank()) { "content must not be blank" }
    }
}

enum class ChatRole {
    USER,
    ASSISTANT,
    SYSTEM,
}
