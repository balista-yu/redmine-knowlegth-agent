package com.example.redmineagent.infrastructure.external.redmine.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Redmine の Journal (チケットコメント / 履歴) 1 件。
 *
 * `notes` は null / 空 もありうる (チャンク化時にスキップ判定される)。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class JournalDto(
    @JsonProperty("id") val id: Int,
    @JsonProperty("user") val user: NamedRefDto,
    @JsonProperty("notes") val notes: String? = null,
    @JsonProperty("created_on") val createdOn: Instant,
)
