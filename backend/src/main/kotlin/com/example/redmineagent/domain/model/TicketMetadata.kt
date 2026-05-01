package com.example.redmineagent.domain.model

/**
 * `TicketChunk` の表示用メタデータ (subject / url / projectName / status / tracker)。
 *
 * Qdrant payload に同梱され、検索結果 (`ScoredChunk`) → `TicketHit` への射影に使う。
 * チャンク単位ではなくチケット単位の情報なので、複数チャンクで同じ値が複製される
 * (Qdrant の payload index は keyword 重複に耐える)。
 */
data class TicketMetadata(
    val subject: String,
    val url: String,
    val projectName: String,
    val status: String,
    val tracker: String,
) {
    init {
        require(subject.isNotBlank()) { "subject must not be blank" }
        require(url.isNotBlank()) { "url must not be blank" }
        require(projectName.isNotBlank()) { "projectName must not be blank" }
        require(status.isNotBlank()) { "status must not be blank" }
        require(tracker.isNotBlank()) { "tracker must not be blank" }
    }

    companion object {
        fun fromIssue(issue: Issue): TicketMetadata =
            TicketMetadata(
                subject = issue.subject,
                url = issue.url,
                projectName = issue.projectName,
                status = issue.status,
                tracker = issue.tracker,
            )
    }
}
