package com.example.redmineagent.infrastructure.external.ollama

import ai.koog.embeddings.base.Embedder
import ai.koog.embeddings.base.Vector
import com.example.redmineagent.application.exception.EmbeddingTooLongException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Ollama 統合テストは作らない方針 (T-1-8 テスト要件)。
 * 例外変換ロジックのみ MockK で検証する。
 */
class OllamaEmbeddingServiceTest {
    private val embedder: Embedder = mockk()
    private val service = OllamaEmbeddingService(embedder)

    @Test
    fun `通常応答は List Double を FloatArray に変換して返す`() =
        runTest {
            coEvery { embedder.embed("hello") } returns Vector(listOf(0.1, 0.2, 0.3))

            val result = service.embed("hello")

            result.size shouldBe 3
            result[0] shouldBe 0.1f
            result[2] shouldBe 0.3f
        }

    @Test
    fun `context length 超過メッセージを含む例外は EmbeddingTooLongException に変換`() =
        runTest {
            coEvery { embedder.embed(any()) } throws RuntimeException("input length exceeds maximum context length")

            shouldThrow<EmbeddingTooLongException> {
                service.embed("very long text")
            }
        }

    @Test
    fun `他のメッセージの例外はそのまま投げる`() =
        runTest {
            coEvery { embedder.embed(any()) } throws RuntimeException("connection refused")

            shouldThrow<RuntimeException> {
                service.embed("anything")
            }.let { thrown ->
                (thrown is EmbeddingTooLongException) shouldBe false
            }
        }

    @Test
    fun `cause に context length メッセージがあっても変換される (chain 探索)`() =
        runTest {
            val cause = RuntimeException("token limit exceeded")
            coEvery { embedder.embed(any()) } throws RuntimeException("upstream call failed", cause)

            shouldThrow<EmbeddingTooLongException> {
                service.embed("anything")
            }
        }
}
