package com.example.redmineagent.infrastructure.external.ollama

import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import com.example.redmineagent.domain.model.ChatMessage
import com.example.redmineagent.domain.model.ChatRole
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * `KoogChatModel` の単体テスト。`OllamaClient.executeStreaming` をモックして
 * `StreamFrame.TextDelta` のみが `LlmDelta` に射影されることを確認する。
 */
class KoogChatModelTest {
    private val ollamaClient = mockk<OllamaClient>()
    private val llmModel =
        LLModel(
            provider = LLMProvider.Ollama,
            id = "test-model",
            capabilities = listOf(LLMCapability.Completion),
        )
    private val chatModel = KoogChatModel(ollamaClient, llmModel)

    @Test
    fun `TextDelta だけが LlmDelta に変換される (End frame は除外)`() =
        runTest {
            val frames =
                listOf(
                    StreamFrame.TextDelta("Hello, ", index = 0),
                    StreamFrame.TextDelta("world", index = 0),
                    StreamFrame.End(finishReason = "stop", metaInfo = ResponseMetaInfo.Empty),
                )
            every {
                ollamaClient.executeStreaming(any(), any(), any())
            } returns flowOf(*frames.toTypedArray())

            val deltas =
                chatModel
                    .chat(listOf(ChatMessage(ChatRole.USER, "Hi")))
                    .toList()

            deltas shouldHaveSize 2
            deltas.map { it.text } shouldBe listOf("Hello, ", "world")
        }

    @Test
    fun `system  user  assistant が順序通り Prompt に積まれる`() =
        runTest {
            val promptSlot = slot<Prompt>()
            every {
                ollamaClient.executeStreaming(capture(promptSlot), any(), any())
            } returns flowOf()

            chatModel
                .chat(
                    listOf(
                        ChatMessage(ChatRole.SYSTEM, "you are helpful"),
                        ChatMessage(ChatRole.USER, "question"),
                        ChatMessage(ChatRole.ASSISTANT, "previous answer"),
                    ),
                ).toList()

            val captured = promptSlot.captured
            captured.messages shouldHaveSize 3
            verify(exactly = 1) { ollamaClient.executeStreaming(any(), llmModel, any()) }
        }
}
