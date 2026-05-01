package com.example.redmineagent.infrastructure.agent.tool

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import com.example.redmineagent.domain.gateway.EmbeddingService
import com.example.redmineagent.domain.model.ScoredChunk
import com.example.redmineagent.domain.model.SearchFilter
import com.example.redmineagent.domain.model.TicketHit
import com.example.redmineagent.domain.repository.TicketChunkRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

/**
 * Koog `Tool` 相当の RAG 検索ツール。LLM が必要に応じて呼び出すと、
 * Embedding → Qdrant 検索を行いチケット粒度の引用候補 (`TicketHit`) を返す。
 *
 * 動作 (docs/02-tasks.md T-2-2):
 *  1. `EmbeddingService.embed(query)` でクエリをベクトル化
 *  2. `TicketChunkRepository.search(vector, limit, filter)` で近傍探索
 *  3. ヒットを ticket_id でグループ化、ベストスコアの chunk を snippet として採用
 *  4. JSON 配列文字列で返却 (Koog `SimpleTool` は `String` 出力前提)
 *
 * `@Tool` アノテーションではなく `SimpleTool<RagSearchArgs>` を継承して直接登録する。
 * これは Spring DI でコンストラクタ注入したまま Koog `ToolRegistry` に積みたいため。
 */
@Component
@Lazy
class RagSearchTool(
    private val embeddingService: EmbeddingService,
    private val ticketChunkRepository: TicketChunkRepository,
) : SimpleTool<RagSearchTool.RagSearchArgs>(
        argsSerializer = RagSearchArgs.serializer(),
        name = TOOL_NAME,
        description = TOOL_DESCRIPTION,
    ) {
    @Serializable
    data class RagSearchArgs(
        val query: String,
        val projectId: Int? = null,
        val statusFilter: String? = null,
        val limit: Int = DEFAULT_LIMIT,
    ) : ToolArgs

    override suspend fun execute(args: RagSearchArgs): String {
        val effectiveLimit = args.limit.coerceIn(1, MAX_LIMIT)
        val vector = embeddingService.embed(args.query)
        val filter =
            SearchFilter(
                projectId = args.projectId,
                status = args.statusFilter,
                tracker = null,
            )
        val hits = ticketChunkRepository.search(vector, effectiveLimit, filter)
        val grouped = groupByTicket(hits, effectiveLimit)
        return jsonCodec.encodeToString(JsonArray.serializer(), JsonArray(grouped.map { it.toJsonObject() }))
    }

    /**
     * chunk 粒度のヒットを ticket_id でまとめ、ticket あたり 1 件の `TicketHit` に変換する。
     * ベストスコアの chunk の content (先頭 SNIPPET_LENGTH 文字) を snippet として採用。
     */
    private fun groupByTicket(
        hits: List<ScoredChunk>,
        limit: Int,
    ): List<TicketHit> {
        val byTicket = hits.groupBy { it.chunk.ticketId }
        return byTicket.values
            .map { chunks -> chunks.maxBy { it.score } }
            .sortedByDescending { it.score }
            .take(limit)
            .map { best ->
                TicketHit(
                    ticketId = best.chunk.ticketId,
                    projectName = best.metadata.projectName,
                    tracker = best.metadata.tracker,
                    status = best.metadata.status,
                    subject = best.metadata.subject,
                    url = best.metadata.url,
                    score = best.score,
                    snippet = best.chunk.content.take(SNIPPET_LENGTH),
                )
            }
    }

    private fun TicketHit.toJsonObject(): JsonObject =
        JsonObject(
            mapOf(
                "ticketId" to JsonPrimitive(ticketId),
                "projectName" to JsonPrimitive(projectName),
                "tracker" to JsonPrimitive(tracker),
                "status" to JsonPrimitive(status),
                "subject" to JsonPrimitive(subject),
                "url" to JsonPrimitive(url),
                "score" to JsonPrimitive(score),
                "snippet" to JsonPrimitive(snippet),
            ),
        )

    companion object {
        const val TOOL_NAME = "ragSearch"
        const val TOOL_DESCRIPTION =
            "Search past Redmine tickets relevant to the given query and return up to `limit` items " +
                "with subject, url, score and snippet. Optional filters: projectId, statusFilter."
        private const val DEFAULT_LIMIT = 5
        private const val MAX_LIMIT = 20
        private const val SNIPPET_LENGTH = 200

        // ToolDescriptor を JVM-static で公開 (KoogConfig からツール一覧表示等で参照可能)
        @JvmField
        val DESCRIPTOR: ToolDescriptor =
            ToolDescriptor(
                name = TOOL_NAME,
                description = TOOL_DESCRIPTION,
                requiredParameters =
                    listOf(
                        ToolParameterDescriptor(
                            name = "query",
                            description = "Search query in natural language",
                            type = ToolParameterType.String,
                        ),
                    ),
                optionalParameters =
                    listOf(
                        ToolParameterDescriptor(
                            name = "projectId",
                            description = "Filter by Redmine project ID (numeric)",
                            type = ToolParameterType.Integer,
                        ),
                        ToolParameterDescriptor(
                            name = "statusFilter",
                            description = "Filter by ticket status name (e.g. 'Closed', 'New')",
                            type = ToolParameterType.String,
                        ),
                        ToolParameterDescriptor(
                            name = "limit",
                            description = "Max number of tickets to return (1..$MAX_LIMIT). Default: $DEFAULT_LIMIT",
                            type = ToolParameterType.Integer,
                        ),
                    ),
            )

        private val jsonCodec = Json { encodeDefaults = true }
    }
}
