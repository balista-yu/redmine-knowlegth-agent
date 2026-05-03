package com.example.redmineagent.infrastructure.external.ollama

import com.example.redmineagent.application.exception.EmbeddingTooLongException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * `/api/embed` を叩く HTTP 実装のテスト。
 *  - レスポンス DTO が新形式 (`embeddings: [[...]]`) を期待することを検証
 *  - 入力長超過 (HTTP 500 + メッセージ) → `EmbeddingTooLongException` 変換を検証
 *
 * MockWebServer (okhttp3) でローカル port を立ててリクエスト/レスポンスを差し替える。
 */
class OllamaEmbeddingServiceTest :
    FunSpec({
        lateinit var server: MockWebServer
        lateinit var service: OllamaEmbeddingService

        beforeTest {
            server = MockWebServer().apply { start() }
            service =
                OllamaEmbeddingService(
                    baseUrl = server.url("/").toString().trimEnd('/'),
                    modelName = "test-embed-model",
                )
        }

        afterTest {
            server.shutdown()
        }

        test("embeddings 配列の先頭ベクトルを FloatArray に変換して返す") {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"model":"test-embed-model","embeddings":[[0.1,0.2,0.3]]}"""),
            )

            val result = service.embed("hello")

            result.size shouldBe 3
            result[0] shouldBe 0.1f
            result[2] shouldBe 0.3f
        }

        test("/api/embed に POST し model と input を JSON で送る") {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"embeddings":[[0.5]]}"""),
            )

            service.embed("hello")

            val recorded = server.takeRequest()
            recorded.method shouldBe "POST"
            recorded.path shouldBe "/api/embed"
            val body = recorded.body.readUtf8()
            (body.contains("\"model\":\"test-embed-model\"")) shouldBe true
            (body.contains("\"input\":\"hello\"")) shouldBe true
        }

        test("HTTP 500 + context length 超過メッセージは EmbeddingTooLongException に変換") {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error":"input length exceeds maximum context length"}"""),
            )

            shouldThrow<EmbeddingTooLongException> {
                service.embed("very long text")
            }
        }

        test("HTTP 500 + 他メッセージはそのまま投げる (EmbeddingTooLongException ではない)") {
            server.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"error":"unrelated server error"}"""),
            )

            val thrown =
                shouldThrow<RuntimeException> {
                    service.embed("anything")
                }
            (thrown is EmbeddingTooLongException) shouldBe false
        }

        test("embeddings 配列が空ならエラー") {
            server.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""{"embeddings":[]}"""),
            )

            shouldThrow<IllegalStateException> {
                service.embed("hello")
            }
        }
    })
