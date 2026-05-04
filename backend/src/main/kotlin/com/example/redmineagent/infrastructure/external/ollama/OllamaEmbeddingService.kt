package com.example.redmineagent.infrastructure.external.ollama

import com.example.redmineagent.application.exception.EmbeddingTooLongException
import com.example.redmineagent.application.exception.OllamaUnavailableException
import com.example.redmineagent.domain.gateway.EmbeddingService
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException

/**
 * Ollama の `/api/embed` (新エンドポイント) を直接叩く EmbeddingService 実装。
 *
 * Koog 0.8.0 の OllamaClient は旧 `/api/embeddings` を叩くがレスポンス DTO は
 * 新形式 (`embeddings: List<Double>` 必須) を期待しており互換性が壊れているため
 * (upstream main で #1854 / #1885 修正済みだが 0.8.0 リリース未取り込み)、
 * Koog 経由を使わず Spring WebClient で直接叩く。
 *
 * Ollama レスポンス形式:
 *   { "model": "...", "embeddings": [[0.1, 0.2, ...]] }
 *
 * 入力長超過は HTTP 500 + メッセージ "input length...exceeds maximum context length" 等で
 * 返ってくるため、メッセージ部分一致で判定し `EmbeddingTooLongException` (Application 層)
 * に変換する (詳細仕様: docs/01-design.md §8.5)。
 */
@Component
class OllamaEmbeddingService(
    @Value("\${app.ollama.base-url}") baseUrl: String,
    @Value("\${app.ollama.embed-model}") private val modelName: String,
) : EmbeddingService {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val webClient: WebClient = WebClient.builder().baseUrl(baseUrl).build()

    @Suppress("TooGenericExceptionCaught") // Ollama / WebClient 例外型は不安定なので RuntimeException 全捕捉
    override suspend fun embed(text: String): FloatArray =
        try {
            val response =
                webClient
                    .post()
                    .uri(EMBED_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(EmbedRequest(model = modelName, input = text))
                    .retrieve()
                    .bodyToMono(EmbedResponse::class.java)
                    .awaitSingle()

            val first =
                response.embeddings.firstOrNull()
                    ?: error("Ollama returned no embeddings for input length=${text.length}")
            FloatArray(first.size) { idx -> first[idx].toFloat() }
        } catch (e: RuntimeException) {
            // 1. context length 超過 → リトライ可能な domain 例外に変換
            if (isContextLengthError(e)) {
                logger.warn("Ollama context length exceeded for input length={}", text.length)
                throw EmbeddingTooLongException(
                    message = "Embedding input too long: length=${text.length}",
                    cause = e,
                )
            }
            // 2. 接続不能 / 5xx (context length 以外) → サービス断として変換
            //    Application 層は OllamaUnavailableException を `is` 判定できる
            if (isOllamaUnavailable(e)) {
                logger.warn("Ollama call failed: {}", e.message)
                throw OllamaUnavailableException(
                    message = "Ollama embed failed: ${e.message}",
                    cause = e,
                )
            }
            throw e
        }

    private fun isOllamaUnavailable(e: Throwable): Boolean =
        when (e) {
            // ConnectException 等が cause チェーンに乗る
            is WebClientRequestException -> true

            // 5xx (context length 判定済みなのでここは純粋な internal error)
            is WebClientResponseException -> e.statusCode.is5xxServerError

            else -> false
        }

    private fun isContextLengthError(e: Throwable): Boolean {
        val messageChain =
            generateSequence(e) { it.cause }
                .map { it.message.orEmpty() }
                .joinToString(" | ")
        // WebClient の 5xx 例外は本文メッセージが responseBodyAsString に入る (message には含まれない場合がある)
        val responseBody = (e as? WebClientResponseException)?.responseBodyAsString.orEmpty()
        val combined = (messageChain + " " + responseBody).lowercase()
        return CONTEXT_LENGTH_KEYWORDS.any { combined.contains(it) }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class EmbedRequest(
        val model: String,
        val input: String,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class EmbedResponse(
        val embeddings: List<List<Double>> = emptyList(),
    )

    companion object {
        private const val EMBED_PATH = "/api/embed"
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
