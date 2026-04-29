package com.example.redmineagent.domain.model

/**
 * Issue の description / journal を 1 チャンク単位で表現したもの。
 * Qdrant に upsert される最小単位。
 *
 * - chunkType=DESCRIPTION: chunkIndex=0 固定 (description 1 つ)
 * - chunkType=JOURNAL:     chunkIndex は 1..N (journal の連番、空 notes は除外後)
 * - 1500 文字超で再分割された場合は subIndex>=1 の追加チャンクが生成される
 */
data class TicketChunk(
    val ticketId: Int,
    val chunkType: ChunkType,
    val chunkIndex: Int,
    val subIndex: Int,
    val content: String,
    val contentHash: String,
) {
    init {
        require(ticketId > 0) { "ticketId must be positive: $ticketId" }
        require(chunkIndex >= 0) { "chunkIndex must be non-negative: $chunkIndex" }
        require(subIndex >= 0) { "subIndex must be non-negative: $subIndex" }
        require(content.isNotBlank()) { "content must not be blank" }
        require(contentHash.isNotBlank()) { "contentHash must not be blank" }
    }
}

enum class ChunkType {
    DESCRIPTION,
    JOURNAL,
}
