package com.example.redmineagent.infrastructure.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * `KoogAgentFactory.build` の最低限の構築テスト。Ollama や LLM への接続は行わず、
 * Bean 構築に必要な引数で `AIAgent` インスタンスが組み立てられることだけを確認する
 * (DoD: Bean 構築テスト pass)。
 */
class KoogAgentFactoryTest {
    @Test
    fun `空の ToolRegistry でも AIAgent を構築できる`() {
        val agent =
            KoogAgentFactory.build(
                promptExecutor = mockk<PromptExecutor>(relaxed = true),
                llmModel =
                    LLModel(
                        provider = LLMProvider.Ollama,
                        id = "test-model",
                        capabilities = listOf(LLMCapability.Completion, LLMCapability.Tools),
                    ),
                toolRegistry = ToolRegistry.EMPTY,
                systemPrompt = "you are a test agent",
                maxIterations = 5,
            )

        agent shouldNotBe null
    }
}
