package com.example.redmineagent.domain.model

/**
 * Qdrant search の生レスポンス 1 件 (chunk 粒度)。
 * `RagSearchTool` の中で ticket 単位 (`TicketHit`) に集約される。
 */
data class ScoredChunk(
    val chunk: TicketChunk,
    val score: Float,
) {
    init {
        require(score >= 0f) { "score must be non-negative: $score" }
    }
}
