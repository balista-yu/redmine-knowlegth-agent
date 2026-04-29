package com.example.redmineagent.domain.model

/**
 * Redmine からのページング取得結果。`offset` + `issues.size` <= `totalCount`。
 */
data class IssuePage(
    val issues: List<Issue>,
    val totalCount: Int,
    val offset: Int,
    val limit: Int,
) {
    init {
        require(totalCount >= 0) { "totalCount must be non-negative: $totalCount" }
        require(offset >= 0) { "offset must be non-negative: $offset" }
        require(limit > 0) { "limit must be positive: $limit" }
    }
}
