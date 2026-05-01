package com.example.redmineagent.domain.repository

import com.example.redmineagent.domain.model.ChatMessage

/**
 * チャット会話履歴の永続化抽象 (`conversationId` 単位)。
 *
 * Infrastructure 層では In-Memory (`ConcurrentHashMap`) で実装される
 * (`InMemoryConversationHistoryRepository`)。将来的に Redis 等への置換余地を残すため
 * Domain 層で interface 化する。
 *
 * `AnswerQuestionApplicationService` (T-2-3) から utilize される。
 */
interface ConversationHistoryRepository {
    /** 会話履歴を取得する。未登録の `conversationId` なら空リスト。 */
    fun get(conversationId: String): List<ChatMessage>

    /** 会話履歴に 1 メッセージ追記する。`conversationId` が未登録なら新規作成。 */
    fun append(
        conversationId: String,
        message: ChatMessage,
    )

    /** 指定 `conversationId` の履歴を破棄する。 */
    fun clear(conversationId: String)
}
