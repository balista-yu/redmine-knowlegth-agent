package com.example.redmineagent.domain.model

/**
 * チャンク + Embedding ベクトル + 表示メタデータ。Qdrant に upsert する点 (Point) の
 * Domain 表現。
 *
 * vector の次元はモデル依存 (例: nomic-embed-text = 768)。`metadata` は payload に
 * 同梱されて検索結果 (`ScoredChunk`) で参照される。
 *
 * `data class` ではなく通常 class:
 *   FloatArray の equals/hashCode は参照ベースのため、value semantics を持つ
 *   data class の生成には不適。dimension の大きい配列を比較する用途も無いので
 *   equals/hashCode は省略する。
 */
class TicketChunkVector(
    val chunk: TicketChunk,
    val vector: FloatArray,
    val metadata: TicketMetadata,
) {
    val dimension: Int get() = vector.size

    init {
        require(vector.isNotEmpty()) { "vector must not be empty" }
    }
}
