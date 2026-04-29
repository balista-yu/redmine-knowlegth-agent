package com.example.redmineagent.domain.repository

import com.example.redmineagent.domain.model.ScoredChunk
import com.example.redmineagent.domain.model.SearchFilter
import com.example.redmineagent.domain.model.TicketChunk
import com.example.redmineagent.domain.model.TicketChunkVector

/**
 * チケットチャンク (description / journal の分割片) を永続化するベクトルストア抽象。
 *
 * Infrastructure 層では Qdrant gRPC クライアントで実装される (`QdrantTicketChunkRepository`)。
 * Domain は UUID v5 等の point ID を意識しない — 識別は (ticketId, chunkType, chunkIndex,
 * subIndex) の組と Domain Model 単位で行う。
 *
 * 詳細仕様: docs/01-design.md §3.2.1, §4.1, §5.1
 */
interface TicketChunkRepository {
    /** 指定 ticket の既存チャンクを全件取得する。差分判定 (hash 比較) で利用。 */
    suspend fun findByTicketId(ticketId: Int): List<TicketChunk>

    /** チャンク (chunk + vector) を upsert する。Point ID 衝突時は上書き。 */
    suspend fun upsert(vectors: List<TicketChunkVector>)

    /**
     * クエリベクトルに対する近傍探索を行う。
     *
     * @param filter `SearchFilter.NONE` の場合は payload フィルタなし
     * @return ヒット順 (score 降順) の chunk + score
     */
    suspend fun search(
        vector: FloatArray,
        limit: Int,
        filter: SearchFilter,
    ): List<ScoredChunk>

    /** 指定 ticket の全チャンクを削除する (Reconcile / 削除済みチケット対応)。 */
    suspend fun deleteByTicketId(ticketId: Int)

    /**
     * 指定 ticket の保持すべき chunk 集合だけを残し、それ以外を削除する。
     * journal が縮小したときに残骸 chunk が残らないよう同期処理から呼ばれる。
     *
     * @param validChunks 保持対象のチャンク (新しいチャンクリスト)
     * @return 削除されたチャンク数
     */
    suspend fun deleteOrphanChunks(
        ticketId: Int,
        validChunks: List<TicketChunk>,
    ): Int

    /** Qdrant 上に存在する全 ticket_id を取得する (Reconcile で Redmine 側と突合)。 */
    suspend fun listAllTicketIds(): Set<Int>
}
