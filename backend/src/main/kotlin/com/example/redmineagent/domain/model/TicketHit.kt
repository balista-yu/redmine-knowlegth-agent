package com.example.redmineagent.domain.model

/**
 * RAG 検索の結果 1 件 (チケット粒度)。複数 chunk hit は ticket 単位に集約され、
 * `score` は最大値、`snippet` はベストスコア chunk の content 抜粋。
 *
 * UI 引用パネル (F-05) の表示単位。
 */
data class TicketHit(
    val ticketId: Int,
    val projectName: String,
    val tracker: String,
    val status: String,
    val subject: String,
    val url: String,
    val score: Float,
    val snippet: String,
) {
    init {
        require(ticketId > 0) { "ticketId must be positive: $ticketId" }
        require(score >= 0f) { "score must be non-negative: $score" }
    }
}
