package com.example.redmineagent.domain.model

import java.time.Instant

/**
 * Redmine 上のチケット 1 件。`description` は本文、`journals` は履歴 (コメント) 群。
 *
 * チャンク生成 (Domain Service `ChunkBuilder`) と Qdrant payload の元データ。
 */
data class Issue(
    val ticketId: Int,
    val projectId: Int,
    val projectName: String,
    val tracker: String,
    val status: String,
    val priority: String,
    val subject: String,
    val author: String,
    val assignee: String?,
    val createdOn: Instant,
    val updatedOn: Instant,
    val url: String,
    val description: String?,
    val journals: List<Journal>,
) {
    init {
        require(ticketId > 0) { "ticketId must be positive: $ticketId" }
        require(projectId > 0) { "projectId must be positive: $projectId" }
        require(subject.isNotBlank()) { "subject must not be blank" }
    }
}
