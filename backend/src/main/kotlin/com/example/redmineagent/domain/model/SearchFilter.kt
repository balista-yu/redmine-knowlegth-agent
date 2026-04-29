package com.example.redmineagent.domain.model

/**
 * RAG 検索時の Qdrant payload フィルタ。
 * すべて省略可で、null は「絞らない」を意味する。
 */
data class SearchFilter(
    val projectId: Int? = null,
    val status: String? = null,
    val tracker: String? = null,
) {
    val isEmpty: Boolean get() = projectId == null && status == null && tracker == null

    companion object {
        val NONE = SearchFilter()
    }
}
