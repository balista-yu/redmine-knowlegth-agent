package com.example.redmineagent.infrastructure.config

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.local.LLMEmbedder
import ai.koog.prompt.executor.ollama.client.OllamaClient
import ai.koog.prompt.executor.ollama.client.OllamaModels
import ai.koog.prompt.llm.LLModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Koog の Ollama クライアント / Embedder Bean 配線。
 *
 * - `OllamaClient` は base URL のみ指定 (HTTP client 等は Koog 側のデフォルトを採用)
 * - Embedding モデルは `app.ollama.embed-model` の値で `OllamaModels.Embeddings` から選択
 *   (未対応モデル名は明示エラー)
 */
@Configuration
class OllamaConfig {
    @Bean
    fun ollamaClient(
        @Value("\${app.ollama.base-url}") baseUrl: String,
    ): OllamaClient = OllamaClient(baseUrl)

    @Bean
    fun ollamaEmbeddingModel(
        @Value("\${app.ollama.embed-model}") modelName: String,
    ): LLModel =
        when (modelName) {
            MODEL_NOMIC -> OllamaModels.Embeddings.NOMIC_EMBED_TEXT
            MODEL_ALL_MINI_LM -> OllamaModels.Embeddings.ALL_MINI_LM
            MODEL_MULTILINGUAL_E5 -> OllamaModels.Embeddings.MULTILINGUAL_E5
            MODEL_BGE_LARGE -> OllamaModels.Embeddings.BGE_LARGE
            MODEL_MXBAI -> OllamaModels.Embeddings.MXBAI_EMBED_LARGE
            else -> error("Unsupported Ollama embedding model: $modelName")
        }

    @Bean
    fun embedder(
        ollamaClient: OllamaClient,
        ollamaEmbeddingModel: LLModel,
    ): Embedder = LLMEmbedder(ollamaClient, ollamaEmbeddingModel)

    companion object {
        private const val MODEL_NOMIC = "nomic-embed-text"
        private const val MODEL_ALL_MINI_LM = "all-minilm"
        private const val MODEL_MULTILINGUAL_E5 = "multilingual-e5"
        private const val MODEL_BGE_LARGE = "bge-large"
        private const val MODEL_MXBAI = "mxbai-embed-large"
    }
}
