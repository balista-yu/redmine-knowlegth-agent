package com.example.redmineagent.infrastructure.agent

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel

/**
 * Koog `AIAgent` を組み立てる薄いファクトリ。
 *
 * Bean 配線とは独立した生成ロジックを持ち、`KoogConfig` から呼ばれる。
 * 単体テストでは Tool 設定のバリエーションを差し替えて検証しやすくなる。
 *
 * - 戦略は `singleRunStrategy()` を使用 (LLM ↔ tool ループの 1 ターン版、最大反復は config 側で制御)
 * - ResponseProcessor / Clock / FeatureContext は Koog のデフォルトに任せる
 *
 * 詳細仕様: docs/01-design.md §6.4
 */
object KoogAgentFactory {
    private const val AGENT_NAME = "redmine-knowledge-agent"

    fun build(
        promptExecutor: PromptExecutor,
        llmModel: LLModel,
        toolRegistry: ToolRegistry,
        systemPrompt: String,
        maxIterations: Int,
    ): AIAgent<String, String> {
        val config =
            AIAgentConfig.withSystemPrompt(
                prompt = systemPrompt,
                llm = llmModel,
                id = AGENT_NAME,
                maxAgentIterations = maxIterations,
            )
        return AIAgent(
            promptExecutor = promptExecutor,
            agentConfig = config,
            strategy = singleRunStrategy(),
            toolRegistry = toolRegistry,
            id = AGENT_NAME,
            installFeatures = {},
        )
    }
}
