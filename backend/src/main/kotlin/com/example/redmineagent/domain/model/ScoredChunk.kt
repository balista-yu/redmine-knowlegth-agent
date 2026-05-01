package com.example.redmineagent.domain.model

/**
 * Qdrant search の生レスポンス 1 件 (chunk 粒度)。
 * `RagSearchTool` の中で ticket 単位 (`TicketHit`) に集約される。
 *
 * `metadata` は同 ticket の subject / url / status などの表示用情報。Qdrant payload
 * から復元される。
 */
data class ScoredChunk(
    val chunk: TicketChunk,
    val metadata: TicketMetadata,
    val score: Float,
) {
    init {
        require(score >= 0f) { "score must be non-negative: $score" }
    }
}
