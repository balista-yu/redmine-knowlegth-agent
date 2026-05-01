package com.example.redmineagent.infrastructure.agent

import com.example.redmineagent.domain.model.ChatMessage
import com.example.redmineagent.domain.repository.ConversationHistoryRepository
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * `ConversationHistoryRepository` の In-Memory 実装 (T-2-1 要件)。
 *
 * - 内部は `ConcurrentHashMap<conversationId, CopyOnWriteArrayList<ChatMessage>>`
 * - 1 プロセス内でのみ有効 (再起動で揮発)。本フェーズではこれで十分
 * - 将来 Redis 等に置き換える際は同じ interface で実装差し替え可能
 */
@Repository
class InMemoryConversationHistoryRepository : ConversationHistoryRepository {
    private val histories = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    override fun get(conversationId: String): List<ChatMessage> = histories[conversationId]?.toList() ?: emptyList()

    override fun append(
        conversationId: String,
        message: ChatMessage,
    ) {
        histories.computeIfAbsent(conversationId) { CopyOnWriteArrayList() } += message
    }

    override fun clear(conversationId: String) {
        histories.remove(conversationId)
    }
}
