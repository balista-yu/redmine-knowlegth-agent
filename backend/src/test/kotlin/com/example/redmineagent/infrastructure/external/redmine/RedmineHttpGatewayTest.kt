package com.example.redmineagent.infrastructure.external.redmine

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * MockWebServer ベースの単体テスト。HTTP メッセージ整形 / DTO パース / Retry 経路を検証。
 *
 * 真の `redmine:5` + `mysql:8` Testcontainers IT は seed スクリプト (T-0-4) 完了後に
 * 別ファイル (`RedmineHttpGatewayIT`) で追加する。
 */
class RedmineHttpGatewayTest {
    private lateinit var server: MockWebServer
    private lateinit var gateway: RedmineHttpGateway

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString().trimEnd('/')
        gateway = RedmineHttpGateway(baseUrl = baseUrl, apiKey = TEST_API_KEY)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `listIssuesUpdatedSince は X-Redmine-API-Key ヘッダ + 必須クエリパラメータを送る`() =
        runTest {
            server.enqueue(MockResponse().setBody(emptyIssuesJson()).setHeader("Content-Type", "application/json"))

            gateway.listIssuesUpdatedSince(since = null, offset = 0, limit = 100)

            val request = server.takeRequest()
            request.getHeader("X-Redmine-API-Key") shouldBe TEST_API_KEY
            val path = request.path ?: ""
            path shouldContain "status_id=*"
            path shouldContain "include=journals"
            path shouldContain "sort=updated_on:desc"
            path shouldContain "limit=100"
            path shouldContain "offset=0"
        }

    @Test
    fun `since が指定されたら updated_on=greater-than-equal-ISO で絞られる`() =
        runTest {
            server.enqueue(MockResponse().setBody(emptyIssuesJson()).setHeader("Content-Type", "application/json"))

            gateway.listIssuesUpdatedSince(
                since = java.time.Instant.parse("2026-04-29T00:00:00Z"),
                offset = 0,
                limit = 100,
            )

            val path = server.takeRequest().path ?: ""
            // 末尾は ISO 文字列。`>` は URL エンコードされる
            path shouldContain "updated_on=%3E%3D2026-04-29T00:00:00Z"
        }

    @Test
    fun `JSON 応答を IssuePage に正しくマップする`() =
        runTest {
            server.enqueue(
                MockResponse()
                    .setBody(sampleIssuesJson())
                    .setHeader("Content-Type", "application/json"),
            )

            val page = gateway.listIssuesUpdatedSince(since = null, offset = 0, limit = 100)

            page.totalCount shouldBe 2
            page.issues shouldHaveSize 2
            page.issues[0].ticketId shouldBe 101
            page.issues[0].subject shouldBe "first"
            page.issues[0].journals shouldHaveSize 1
            page.issues[0].journals[0].notes shouldBe "first comment"
            page.issues[1].assignee shouldBe "bob"
        }

    @Test
    fun `5xx で 3 回まで retry したあと最終的に応答を取得できる`() =
        runTest {
            // 2 回 5xx, 3 回目で成功
            repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }
            server.enqueue(MockResponse().setBody(emptyIssuesJson()).setHeader("Content-Type", "application/json"))

            val page = gateway.listIssuesUpdatedSince(since = null, offset = 0, limit = 100)

            page.issues shouldHaveSize 0
            server.requestCount shouldBe 3
        }

    private fun emptyIssuesJson(): String =
        """
        {"issues": [], "total_count": 0, "offset": 0, "limit": 100}
        """.trimIndent()

    private fun sampleIssuesJson(): String =
        """
        {
          "issues": [
            {
              "id": 101,
              "project": {"id": 1, "name": "alpha"},
              "tracker": {"id": 1, "name": "Bug"},
              "status": {"id": 1, "name": "New"},
              "priority": {"id": 1, "name": "Normal"},
              "subject": "first",
              "description": "body",
              "author": {"id": 1, "name": "alice"},
              "created_on": "2026-04-29T10:00:00Z",
              "updated_on": "2026-04-29T10:00:00Z",
              "journals": [
                {"id": 1, "user": {"id": 2, "name": "carol"}, "notes": "first comment", "created_on": "2026-04-29T10:30:00Z"}
              ]
            },
            {
              "id": 102,
              "project": {"id": 1, "name": "alpha"},
              "tracker": {"id": 1, "name": "Task"},
              "status": {"id": 2, "name": "InProgress"},
              "priority": {"id": 2, "name": "High"},
              "subject": "second",
              "description": null,
              "author": {"id": 1, "name": "alice"},
              "assigned_to": {"id": 3, "name": "bob"},
              "created_on": "2026-04-29T11:00:00Z",
              "updated_on": "2026-04-29T11:00:00Z",
              "journals": []
            }
          ],
          "total_count": 2,
          "offset": 0,
          "limit": 100
        }
        """.trimIndent()

    companion object {
        private const val TEST_API_KEY = "test-api-key"
    }
}
