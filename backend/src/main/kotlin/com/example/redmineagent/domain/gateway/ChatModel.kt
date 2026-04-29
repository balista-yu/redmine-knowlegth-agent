package com.example.redmineagent.domain.gateway

import com.example.redmineagent.domain.model.ChatMessage
import com.example.redmineagent.domain.model.LlmDelta
import kotlinx.coroutines.flow.Flow

/**
 * チャット LLM 抽象 (ストリーム応答)。
 *
 * Infrastructure 層では Koog `prompt-executor-ollama-client` をラップして実装される
 * (`KoogChatModel`)。Domain は HTTP / SSE / SDK 型を意識しない。
 *
 * `AnswerQuestionApplicationService` は Koog の `AIAgent` を直接注入される (設計書 §6.4
 * の妥協点)。`ChatModel` 自体は単純なチャット用途 (将来の非エージェント呼び出しを想定)
 * に残してある。
 *
 * 詳細仕様: docs/01-design.md §3.2.4 (F-04), §6.4
 */
interface ChatModel {
    /** 会話履歴を投げて、応答テキストを 1 トークン分の `LlmDelta` 単位でストリーム返却する。 */
    fun chat(messages: List<ChatMessage>): Flow<LlmDelta>
}
