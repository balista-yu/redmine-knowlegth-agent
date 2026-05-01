package com.example.redmineagent.infrastructure.external.ollama

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import com.example.redmineagent.application.exception.EmbeddingTooLongException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk

/**
 * Ollama 統合テストは作らない方針 (T-1-8 テスト要件)。
 * 例外変換ロジックのみ MockK で検証する。
 */
class OllamaEmbeddingServiceTest :
    FunSpec({
        val embedder: Embedder = mockk()
        val service = OllamaEmbeddingService(embedder)

        test("通常応答は List Double を FloatArray に変換して返す") {
            coEvery { embedder.embed("hello") } returns Vector(listOf(0.1, 0.2, 0.3))

            val result = service.embed("hello")

            result.size shouldBe 3
            result[0] shouldBe 0.1f
            result[2] shouldBe 0.3f
        }

        test("context length 超過メッセージを含む例外は EmbeddingTooLongException に変換") {
            coEvery { embedder.embed(any()) } throws RuntimeException("input length exceeds maximum context length")

            shouldThrow<EmbeddingTooLongException> {
                service.embed("very long text")
            }
        }

        test("他のメッセージの例外はそのまま投げる") {
            coEvery { embedder.embed(any()) } throws RuntimeException("connection refused")

            shouldThrow<RuntimeException> {
                service.embed("anything")
            }.let { thrown ->
                (thrown is EmbeddingTooLongException) shouldBe false
            }
        }

        test("cause に context length メッセージがあっても変換される (chain 探索)") {
            val cause = RuntimeException("token limit exceeded")
            coEvery { embedder.embed(any()) } throws RuntimeException("upstream call failed", cause)

            shouldThrow<EmbeddingTooLongException> {
                service.embed("anything")
            }
        }
    })
