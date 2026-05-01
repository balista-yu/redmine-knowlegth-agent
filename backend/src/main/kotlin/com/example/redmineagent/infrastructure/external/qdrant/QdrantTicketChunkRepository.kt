package com.example.redmineagent.infrastructure.external.qdrant

import com.example.redmineagent.domain.model.ChunkType
import com.example.redmineagent.domain.model.ScoredChunk
import com.example.redmineagent.domain.model.SearchFilter
import com.example.redmineagent.domain.model.TicketChunk
import com.example.redmineagent.domain.model.TicketChunkVector
import com.example.redmineagent.domain.model.TicketMetadata
import com.example.redmineagent.domain.repository.TicketChunkRepository
import io.qdrant.client.ConditionFactory.matchKeyword
import io.qdrant.client.PointIdFactory.id
import io.qdrant.client.QdrantClient
import io.qdrant.client.ValueFactory.value
import io.qdrant.client.VectorsFactory.vectors
import io.qdrant.client.grpc.Collections.Distance
import io.qdrant.client.grpc.Collections.PayloadIndexParams
import io.qdrant.client.grpc.Collections.PayloadSchemaType
import io.qdrant.client.grpc.Collections.VectorParams
import io.qdrant.client.grpc.Common.Filter
import io.qdrant.client.grpc.Common.PointId
import io.qdrant.client.grpc.Points.PointStruct
import io.qdrant.client.grpc.Points.ScrollPoints
import io.qdrant.client.grpc.Points.SearchPoints
import io.qdrant.client.grpc.Points.WithPayloadSelector
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Repository
import java.time.Duration
import io.qdrant.client.grpc.JsonWithInt.Value as JsonValue

/**
 * `TicketChunkRepository` の Qdrant gRPC 実装。
 *
 * - 起動時にコレクション存在確認、無ければ自動作成 (vector size, Cosine, payload index)
 * - upsert は batch 対応 (UPSERT_BATCH_SIZE 件 / batch)
 * - filter は ticket_id / project_id / status / tracker
 *
 * Qdrant Java client 1.17.0 は `ListenableFuture` (Guava) ベースなので、`.get()` を
 * `withContext(Dispatchers.IO)` 内で blocking 呼び出しする (kotlinx-coroutines-future
 * は CompletionStage 用で適合しない)。
 *
 * 詳細仕様: docs/01-design.md §4.1 (payload), §8.3
 */
@Repository
@Lazy
@Suppress("TooManyFunctions")
class QdrantTicketChunkRepository(
    private val client: QdrantClient,
    @Value("\${app.qdrant.collection}") private val collectionName: String,
    @Value("\${app.qdrant.vector-size:768}") private val vectorSize: Int,
) : TicketChunkRepository {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostConstruct
    fun ensureCollection() {
        val exists = client.collectionExistsAsync(collectionName).get()
        if (exists) {
            logger.info("Qdrant collection already exists: {}", collectionName)
            return
        }
        logger.info("Creating Qdrant collection: name={} vectorSize={}", collectionName, vectorSize)
        val params =
            VectorParams
                .newBuilder()
                .setSize(vectorSize.toLong())
                .setDistance(Distance.Cosine)
                .build()
        client.createCollectionAsync(collectionName, params).get()
        // payload index (フィルタリング高速化)
        listOf(
            PAYLOAD_TICKET_ID to PayloadSchemaType.Integer,
            PAYLOAD_PROJECT_ID to PayloadSchemaType.Integer,
            PAYLOAD_STATUS to PayloadSchemaType.Keyword,
            PAYLOAD_TRACKER to PayloadSchemaType.Keyword,
            PAYLOAD_UPDATED_ON to PayloadSchemaType.Datetime,
        ).forEach { (field, type) ->
            client
                .createPayloadIndexAsync(
                    collectionName,
                    field,
                    type,
                    PayloadIndexParams.getDefaultInstance(),
                    // wait =
                    true,
                    // ordering =
                    null,
                    // timeout =
                    Duration.ofSeconds(INDEX_TIMEOUT_SECONDS),
                ).get()
        }
    }

    override suspend fun upsert(vectors: List<TicketChunkVector>) {
        if (vectors.isEmpty()) return
        withContext(Dispatchers.IO) {
            vectors.chunked(UPSERT_BATCH_SIZE).forEach { batch ->
                client.upsertAsync(collectionName, batch.map { it.toPoint() }).get()
            }
        }
    }

    override suspend fun findByTicketId(ticketId: Int): List<TicketChunk> =
        withContext(Dispatchers.IO) {
            val request =
                ScrollPoints
                    .newBuilder()
                    .setCollectionName(collectionName)
                    .setFilter(byTicketIdFilter(ticketId))
                    .setLimit(MAX_SCROLL_PAGE)
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build()
            client
                .scrollAsync(request)
                .get()
                .resultList
                .mapNotNull { it.payloadMap.toTicketChunk() }
        }

    override suspend fun search(
        vector: FloatArray,
        limit: Int,
        filter: SearchFilter,
    ): List<ScoredChunk> =
        withContext(Dispatchers.IO) {
            val builder =
                SearchPoints
                    .newBuilder()
                    .setCollectionName(collectionName)
                    .setLimit(limit.toLong())
                    .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
            vector.forEach { builder.addVector(it) }
            buildFilter(filter)?.let { builder.setFilter(it) }
            client.searchAsync(builder.build()).get().mapNotNull { hit ->
                val chunk = hit.payloadMap.toTicketChunk() ?: return@mapNotNull null
                val metadata = hit.payloadMap.toTicketMetadata() ?: return@mapNotNull null
                ScoredChunk(chunk = chunk, metadata = metadata, score = hit.score)
            }
        }

    override suspend fun deleteByTicketId(ticketId: Int) {
        withContext(Dispatchers.IO) {
            client.deleteAsync(collectionName, byTicketIdFilter(ticketId)).get()
        }
    }

    override suspend fun deleteOrphanChunks(
        ticketId: Int,
        validChunks: List<TicketChunk>,
    ): Int {
        val existing = findByTicketId(ticketId)
        val validKeys =
            validChunks
                .map { Triple(it.chunkType, it.chunkIndex, it.subIndex) }
                .toSet()
        val orphans =
            existing.filter { Triple(it.chunkType, it.chunkIndex, it.subIndex) !in validKeys }
        if (orphans.isEmpty()) return 0
        withContext(Dispatchers.IO) {
            val ids: List<PointId> = orphans.map { id(PointIdGenerator.pointId(it)) }
            client.deleteAsync(collectionName, ids).get()
        }
        return orphans.size
    }

    override suspend fun listAllTicketIds(): Set<Int> =
        withContext(Dispatchers.IO) {
            val ids = mutableSetOf<Int>()
            var nextOffset: PointId? = null
            while (true) {
                val builder =
                    ScrollPoints
                        .newBuilder()
                        .setCollectionName(collectionName)
                        .setLimit(MAX_SCROLL_PAGE)
                        .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                if (nextOffset != null) builder.setOffset(nextOffset)
                val resp = client.scrollAsync(builder.build()).get()
                resp.resultList.forEach { p ->
                    val v = p.payloadMap[PAYLOAD_TICKET_ID]
                    if (v != null) ids += v.integerValue.toInt()
                }
                if (!resp.hasNextPageOffset()) break
                nextOffset = resp.nextPageOffset
            }
            ids
        }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private fun TicketChunkVector.toPoint(): PointStruct {
        val pid = PointIdGenerator.pointId(chunk)
        val payload =
            mapOf(
                // チャンク識別子 + 内容
                PAYLOAD_TICKET_ID to value(chunk.ticketId.toLong()),
                PAYLOAD_CHUNK_TYPE to value(chunk.chunkType.name.lowercase()),
                PAYLOAD_CHUNK_INDEX to value(chunk.chunkIndex.toLong()),
                PAYLOAD_SUB_INDEX to value(chunk.subIndex.toLong()),
                PAYLOAD_CONTENT to value(chunk.content),
                PAYLOAD_CONTENT_HASH to value(chunk.contentHash),
                // 表示メタデータ (TicketHit に投影)
                PAYLOAD_SUBJECT to value(metadata.subject),
                PAYLOAD_URL to value(metadata.url),
                PAYLOAD_PROJECT_NAME to value(metadata.projectName),
                PAYLOAD_STATUS to value(metadata.status),
                PAYLOAD_TRACKER to value(metadata.tracker),
            )
        return PointStruct
            .newBuilder()
            .setId(id(pid))
            .setVectors(vectors(*vector))
            .putAllPayload(payload)
            .build()
    }

    private fun byTicketIdFilter(ticketId: Int): Filter =
        Filter
            .newBuilder()
            .addMust(matchKeyword(PAYLOAD_TICKET_ID, ticketId.toString()))
            .build()

    private fun buildFilter(filter: SearchFilter): Filter? {
        if (filter.isEmpty) return null
        val builder = Filter.newBuilder()
        filter.projectId?.let { builder.addMust(matchKeyword(PAYLOAD_PROJECT_ID, it.toString())) }
        filter.status?.let { builder.addMust(matchKeyword(PAYLOAD_STATUS, it)) }
        filter.tracker?.let { builder.addMust(matchKeyword(PAYLOAD_TRACKER, it)) }
        return builder.build()
    }

    @Suppress("ReturnCount") // 6 つの payload field それぞれの欠損を null 返却で表現するため
    private fun Map<String, JsonValue>.toTicketChunk(): TicketChunk? {
        val ticketId = this[PAYLOAD_TICKET_ID]?.integerValue?.toInt() ?: return null
        val chunkTypeStr = this[PAYLOAD_CHUNK_TYPE]?.stringValue ?: return null
        val chunkIndex = this[PAYLOAD_CHUNK_INDEX]?.integerValue?.toInt() ?: return null
        val subIndex = this[PAYLOAD_SUB_INDEX]?.integerValue?.toInt() ?: 0
        val content = this[PAYLOAD_CONTENT]?.stringValue ?: return null
        val contentHash = this[PAYLOAD_CONTENT_HASH]?.stringValue ?: return null
        return TicketChunk(
            ticketId = ticketId,
            chunkType = ChunkType.valueOf(chunkTypeStr.uppercase()),
            chunkIndex = chunkIndex,
            subIndex = subIndex,
            content = content,
            contentHash = contentHash,
        )
    }

    @Suppress("ReturnCount") // 5 つの metadata field それぞれの欠損を null 返却で表現
    private fun Map<String, JsonValue>.toTicketMetadata(): TicketMetadata? {
        val subject = this[PAYLOAD_SUBJECT]?.stringValue ?: return null
        val url = this[PAYLOAD_URL]?.stringValue ?: return null
        val projectName = this[PAYLOAD_PROJECT_NAME]?.stringValue ?: return null
        val status = this[PAYLOAD_STATUS]?.stringValue ?: return null
        val tracker = this[PAYLOAD_TRACKER]?.stringValue ?: return null
        return TicketMetadata(
            subject = subject,
            url = url,
            projectName = projectName,
            status = status,
            tracker = tracker,
        )
    }

    companion object {
        private const val UPSERT_BATCH_SIZE = 32
        private const val MAX_SCROLL_PAGE = 256
        private const val INDEX_TIMEOUT_SECONDS = 10L
        private const val PAYLOAD_TICKET_ID = "ticket_id"
        private const val PAYLOAD_PROJECT_ID = "project_id"
        private const val PAYLOAD_STATUS = "status"
        private const val PAYLOAD_TRACKER = "tracker"
        private const val PAYLOAD_UPDATED_ON = "updated_on"
        private const val PAYLOAD_CHUNK_TYPE = "chunk_type"
        private const val PAYLOAD_CHUNK_INDEX = "chunk_index"
        private const val PAYLOAD_SUB_INDEX = "sub_index"
        private const val PAYLOAD_CONTENT = "content"
        private const val PAYLOAD_CONTENT_HASH = "content_hash"
        private const val PAYLOAD_SUBJECT = "subject"
        private const val PAYLOAD_URL = "url"
        private const val PAYLOAD_PROJECT_NAME = "project_name"
    }
}
