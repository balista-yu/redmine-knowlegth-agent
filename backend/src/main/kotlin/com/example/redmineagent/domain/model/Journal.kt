package com.example.redmineagent.domain.model

import java.time.Instant

/**
 * Redmine の journal (コメント) 1 件。
 * `notes` は空 / null でもよい (チャンク化時に空ノートはスキップされる)。
 */
data class Journal(
    val notes: String?,
    val author: String,
    val createdOn: Instant,
)
