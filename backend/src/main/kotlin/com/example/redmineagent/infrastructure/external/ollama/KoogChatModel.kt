package com.example.redmineagent.infrastructure.external.ollama

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.streaming.StreamFrame
import com.example.redmineagent.domain.gateway.ChatModel
import com.example.redmineagent.domain.model.ChatMessage
import com.example.redmineagent.domain.model.ChatRole
import com.example.redmineagent.domain.model.LlmDelta
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * `ChatModel` の Koog (Ollama) 実装。
 *
 * - `OllamaClient.executeStreaming` で Ollama にストリーミング問い合わせ
 * - `StreamFrame.TextDelta` のみを `LlmDelta` に射影 (ToolCall / Reasoning frame は無視)
 * - tool は受け取らない (Domain interface が提供しない)。Tool 統合は `AIAgent` 経由で
 *   `AnswerQuestionApplicationService` (T-2-3) が担う
 *
 * 詳細仕様: docs/01-design.md §3.2.4 (F-04), §6.4
 */
@Component
@Lazy
class KoogChatModel(
    private val ollamaClient: OllamaClient,
    private val ollamaChatLlm: LLModel,
) : ChatModel {
    override fun chat(messages: List<ChatMessage>): Flow<LlmDelta> {
        val prompt = buildPrompt(messages)
        return ollamaClient
            .executeStreaming(prompt, ollamaChatLlm, emptyList())
            .filterIsInstance<StreamFrame.TextDelta>()
            .map { LlmDelta(text = it.text) }
    }

    private fun buildPrompt(messages: List<ChatMessage>): Prompt {
        val builder = Prompt.builder(PROMPT_ID)
        for (message in messages) {
            when (message.role) {
                ChatRole.SYSTEM -> builder.system(message.content)
                ChatRole.USER -> builder.user(message.content)
                ChatRole.ASSISTANT -> builder.assistant(message.content)
            }
        }
        return builder.build()
    }

    companion object {
        // Prompt ID は Koog 内部のロギング/トレース用識別子 (空でも可だが任意名を付けておく)
        private const val PROMPT_ID = "redmine-agent-chat"
    }
}
