package com.example.redmineagent.infrastructure.external.redmine

import com.example.redmineagent.domain.gateway.RedmineGateway
import com.example.redmineagent.domain.model.IssuePage
import com.example.redmineagent.infrastructure.external.redmine.dto.IssueListResponseDto
import com.example.redmineagent.infrastructure.external.redmine.mapper.toDomain
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.codec.json.JacksonJsonDecoder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * `RedmineGateway` の Spring `WebClient` 実装。
 *
 * - `X-Redmine-API-Key` ヘッダで認証
 * - 5xx / Timeout で指数バックオフ 3 回リトライ
 * - JSON: snake_case フィールドは DTO 側で `@JsonProperty` 明示
 */
@Component
class RedmineHttpGateway(
    @Value("\${app.redmine.base-url}") private val baseUrl: String,
    @Value("\${app.redmine.api-key}") private val apiKey: String,
) : RedmineGateway {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Spring Boot 4.0 では Spring Web の codecs が tools.jackson 系 (Jackson 3.x) に移行。
    // ObjectMapper bean の autowire は状況により不安定なので、Kotlin module を明示した
    // JsonMapper を自前で構成する (java.time は Jackson 3.x core でデフォルトサポート済み)
    private val jsonMapper: JsonMapper =
        JsonMapper
            .builder()
            .addModule(kotlinModule())
            .build()

    private val client: WebClient =
        WebClient
            .builder()
            .baseUrl(baseUrl)
            .defaultHeader(API_KEY_HEADER, apiKey)
            .codecs { it.defaultCodecs().jacksonJsonDecoder(JacksonJsonDecoder(jsonMapper)) }
            .build()

    override suspend fun listIssuesUpdatedSince(
        since: Instant?,
        offset: Int,
        limit: Int,
    ): IssuePage {
        val response = fetchIssuesPage(since, offset, limit)
        return response.toDomain(baseUrl)
    }

    override suspend fun listAllIssueIds(): Set<Int> {
        val ids = mutableSetOf<Int>()
        var offset = 0
        while (true) {
            val page = fetchIssuesPage(since = null, offset = offset, limit = PAGE_LIMIT)
            page.issues.forEach { ids += it.id }
            offset += page.issues.size
            if (offset >= page.totalCount || page.issues.isEmpty()) break
        }
        return ids
    }

    private suspend fun fetchIssuesPage(
        since: Instant?,
        offset: Int,
        limit: Int,
    ): IssueListResponseDto =
        client
            .get()
            .uri { builder ->
                builder
                    .path("/issues.json")
                    .queryParam("status_id", "*")
                    .queryParam("include", "journals")
                    .queryParam("sort", "updated_on:desc")
                    .queryParam("limit", limit)
                    .queryParam("offset", offset)
                    .apply {
                        if (since != null) {
                            queryParam("updated_on", ">=" + DateTimeFormatter.ISO_INSTANT.format(since))
                        }
                    }.build()
            }.header(HttpHeaders.ACCEPT, "application/json")
            .retrieve()
            .bodyToMono(IssueListResponseDto::class.java)
            .retryWhen(retrySpec())
            .awaitSingle()

    private fun retrySpec(): Retry =
        Retry
            .backoff(MAX_RETRIES.toLong(), Duration.ofMillis(RETRY_INITIAL_BACKOFF_MS))
            .maxBackoff(Duration.ofSeconds(RETRY_MAX_BACKOFF_SECONDS))
            .filter { it is WebClientResponseException && it.statusCode.is5xxServerError }
            .doBeforeRetry {
                logger.warn(
                    "Retrying Redmine call (attempt={}, cause={})",
                    it.totalRetries() + 1,
                    it.failure().javaClass.simpleName,
                )
            }

    companion object {
        private const val API_KEY_HEADER = "X-Redmine-API-Key"
        private const val MAX_RETRIES = 3
        private const val RETRY_INITIAL_BACKOFF_MS = 500L
        private const val RETRY_MAX_BACKOFF_SECONDS = 5L
        private const val PAGE_LIMIT = 100
    }
}
