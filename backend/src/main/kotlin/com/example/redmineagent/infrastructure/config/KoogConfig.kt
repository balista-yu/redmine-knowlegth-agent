package com.example.redmineagent.infrastructure.config

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import com.example.redmineagent.infrastructure.agent.KoogAgentFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Lazy

/**
 * Koog `AIAgent` 周りの Bean 配線。
 *
 * 構成:
 *  - `ollamaChatLlm`  : チャット用 LLM (`app.ollama.llm-model` で指定。例: qwen2.5:7b)
 *  - `promptExecutor` : `SingleLLMPromptExecutor(ollamaClient)` (Koog の標準ラッパー)
 *  - `toolRegistry`   : 空。T-2-2 で `RagSearchTool` を含む `ToolRegistry` に置き換える予定
 *  - `aiAgent`        : `AIAgent<String, String>`。`KoogAgentFactory.build` で構築
 *
 * すべて `@Lazy` にして HealthCheckTest 等の eager 初期化チェーンを避ける。Ollama / Qdrant
 * への実接続は実際のチャット呼び出し時まで遅延する。
 */
@Configuration
class KoogConfig {
    @Bean
    @Lazy
    fun ollamaChatLlm(
        @Value("\${app.ollama.llm-model}") modelId: String,
    ): LLModel =
        LLModel(
            provider = LLMProvider.Ollama,
            id = modelId,
            capabilities =
                listOf(
                    LLMCapability.Completion,
                    LLMCapability.Tools,
                    LLMCapability.Temperature,
                ),
        )

    // Koog 0.8.x で SingleLLMPromptExecutor は deprecated。MultiLLMPromptExecutor の vararg
    // 形式 (LLMClient...) で provider マッピングを Koog 内部に任せる
    @Bean
    @Lazy
    fun promptExecutor(ollamaClient: OllamaClient): PromptExecutor = MultiLLMPromptExecutor(ollamaClient)

    // T-2-2 で RagSearchTool を含む ToolRegistry に置き換える予定 (本フェーズは空で起動できることを優先)
    @Bean
    @Lazy
    fun toolRegistry(): ToolRegistry = ToolRegistry.EMPTY

    @Bean
    @Lazy
    fun aiAgent(
        promptExecutor: PromptExecutor,
        ollamaChatLlm: LLModel,
        toolRegistry: ToolRegistry,
        @Value("\${app.agent.system-prompt}") systemPrompt: String,
        @Value("\${app.agent.max-iterations}") maxIterations: Int,
    ): AIAgent<String, String> =
        KoogAgentFactory.build(
            promptExecutor = promptExecutor,
            llmModel = ollamaChatLlm,
            toolRegistry = toolRegistry,
            systemPrompt = systemPrompt,
            maxIterations = maxIterations,
        )
}
