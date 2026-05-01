package com.example.redmineagent.infrastructure.agent

import com.example.redmineagent.domain.model.ChatMessage
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * conversationId 単位の会話履歴を In-Memory で保持するストア (T-2-1 要件)。
 *
 * - 内部は `ConcurrentHashMap<conversationId, CopyOnWriteArrayList<ChatMessage>>`
 * - 1 プロセス内でのみ有効 (再起動で揮発)。本フェーズではこれで十分
 * - 将来 Redis 等に置き換える際は同じインターフェースで実装差し替え可能
 *
 * `AnswerQuestionApplicationService` (T-2-3) から append / get で利用する。
 */
@Component
class ConversationHistoryStore {
    private val histories = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    /** 会話履歴を取得する。未登録の conversationId なら空リスト。 */
    fun get(conversationId: String): List<ChatMessage> = histories[conversationId]?.toList() ?: emptyList()

    /** 会話履歴に 1 メッセージ追記する。conversationId が未登録なら新規作成。 */
    fun append(
        conversationId: String,
        message: ChatMessage,
    ) {
        histories.computeIfAbsent(conversationId) { CopyOnWriteArrayList() } += message
    }

    /** 指定 conversationId の履歴を破棄する。 */
    fun clear(conversationId: String) {
        histories.remove(conversationId)
    }
}
