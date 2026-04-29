package com.example.redmineagent.infrastructure.external.ollama

import ai.koog.embeddings.base.Embedder
import com.example.redmineagent.application.exception.EmbeddingTooLongException
import com.example.redmineagent.domain.gateway.EmbeddingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * `EmbeddingService` の Koog `Embedder` 実装ラッパ。
 *
 * - `Embedder` (= `LLMEmbedder`) を内包し、結果の `Vector` を `FloatArray` に変換
 * - context length 超過と思われる例外を `EmbeddingTooLongException` (Application 層) に変換
 *
 * Ollama の context length 超過は HTTP 500 + メッセージ "input length...exceeds maximum
 * context length" 等として返ってくるため、メッセージ部分一致で判定する (詳細仕様: docs/01-design.md §8.5)。
 */
@Component
class OllamaEmbeddingService(
    private val embedder: Embedder,
) : EmbeddingService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Suppress("TooGenericExceptionCaught") // Koog / Ollama 側の例外型は不安定なので RuntimeException 全捕捉
    override suspend fun embed(text: String): FloatArray =
        try {
            val vector = embedder.embed(text)
            vector.values.map { it.toFloat() }.toFloatArray()
        } catch (e: RuntimeException) {
            if (isContextLengthError(e)) {
                logger.warn("Ollama context length exceeded for input length={}", text.length)
                throw EmbeddingTooLongException(
                    message = "Embedding input too long: length=${text.length}",
                    cause = e,
                )
            }
            throw e
        }

    private fun isContextLengthError(e: Throwable): Boolean {
        val combined =
            generateSequence(e) { it.cause }
                .map { it.message.orEmpty() }
                .joinToString(" | ")
                .lowercase()
        return CONTEXT_LENGTH_KEYWORDS.any { combined.contains(it) }
    }

    companion object {
        private val CONTEXT_LENGTH_KEYWORDS =
            listOf(
                "context length",
                "maximum context",
                "token limit",
                "exceeds the maximum",
                "input is too long",
            )
    }
}
