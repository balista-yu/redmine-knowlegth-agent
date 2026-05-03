package com.example.redmineagent.infrastructure.config

import ai.koog.prompt.executor.ollama.client.OllamaClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Koog の Ollama クライアント Bean 配線。
 *
 * - `OllamaClient` は Koog の Chat (LLM) 用途でのみ使用 (KoogConfig.promptExecutor が消費)
 * - Embedding は Koog 0.8.0 の互換性問題のため独自実装 (`OllamaEmbeddingService`) で
 *   Spring WebClient 経由で `/api/embed` を直接叩く。Embedder Bean はここでは配線しない
 */
@Configuration
class OllamaConfig {
    @Bean
    fun ollamaClient(
        @Value("\${app.ollama.base-url}") baseUrl: String,
    ): OllamaClient = OllamaClient(baseUrl)
}
