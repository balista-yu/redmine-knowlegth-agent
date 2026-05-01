package com.example.redmineagent.application.service

import ai.koog.agents.core.agent.AIAgent
import com.example.redmineagent.domain.model.AgentEvent
import com.example.redmineagent.domain.model.ChatMessage
import com.example.redmineagent.domain.model.ChatRole
import com.example.redmineagent.domain.repository.ConversationHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * F-04 (チャット応答) のユースケース実装。
 *
 * AIAgent (Koog) を直接注入される — これは設計書 §6.4 で明記された妥協点
 * (`.claude/rules/application-layer.md` 第 4 項)。
 *
 * フロー:
 *  1. `conversationId` 未指定なら UUID v4 を採番
 *  2. 既存履歴を `ConversationHistoryRepository.get` で取得 (Phase 2 では prompt 結合は未実施。
 *     Koog AIAgent は単発 input → output の単純な run() 呼び出しを使う)
 *  3. user メッセージを履歴に append
 *  4. `AIAgent.run(message)` で応答を取得
 *  5. 成功時: `Delta(response)` → `Done` を順に発行、assistant メッセージを履歴に append
 *  6. 失敗時: `Error(code, message)` で flow を終端
 *
 * 注: 本フェーズではトークン単位ストリーミング (`Delta` 連続発行) と
 * `Sources` イベントは未実装。`AIAgent` の Koog 内部ストリーム / Tool 呼び出しを
 * `EventHandler` Feature で hook する高度版は T-2-4 ChatController 完成後の
 * フォローアップで対応 (DoD: 主要フロー + Error 出力)。
 */
@Service
@Lazy
class AnswerQuestionApplicationService(
    private val agent: AIAgent<String, String>,
    private val conversationHistory: ConversationHistoryRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    fun execute(
        message: String,
        conversationId: String?,
    ): Flow<AgentEvent> =
        flow {
            val cid = conversationId ?: UUID.randomUUID().toString()
            conversationHistory.append(cid, ChatMessage(ChatRole.USER, message))
            // Koog/Ollama/Qdrant 由来の任意例外を Error イベントに統一変換するため Exception で受ける
            @Suppress("TooGenericExceptionCaught")
            try {
                val response = agent.run(message)
                emit(AgentEvent.Delta(response))
                conversationHistory.append(cid, ChatMessage(ChatRole.ASSISTANT, response))
                emit(AgentEvent.Done)
            } catch (e: Exception) {
                logger.warn("Agent execution failed: cid={}, error={}", cid, e.message, e)
                emit(toErrorEvent(e))
            }
        }

    /**
     * Infrastructure (SDK) 例外 → API エラーコードへのマッピング (docs/03-api-spec.md §1)。
     * 例外メッセージから簡易判定 — 本来は SDK 例外型で分岐したいが、Koog は
     * RuntimeException を直に投げる場合があるためメッセージベースで判定する。
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun toErrorEvent(e: Exception): AgentEvent.Error {
        val msg = e.message.orEmpty().lowercase()
        val code =
            when {
                "ollama" in msg || "connection refused" in msg -> CODE_OLLAMA_UNAVAILABLE
                "qdrant" in msg -> CODE_QDRANT_UNAVAILABLE
                else -> CODE_INTERNAL
            }
        return AgentEvent.Error(code = code, message = e.message ?: e::class.java.simpleName)
    }

    companion object {
        private const val CODE_OLLAMA_UNAVAILABLE = "OLLAMA_UNAVAILABLE"
        private const val CODE_QDRANT_UNAVAILABLE = "QDRANT_UNAVAILABLE"
        private const val CODE_INTERNAL = "INTERNAL"
    }
}
