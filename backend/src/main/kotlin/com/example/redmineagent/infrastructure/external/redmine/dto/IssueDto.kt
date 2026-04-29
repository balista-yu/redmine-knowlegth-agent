package com.example.redmineagent.infrastructure.external.redmine.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Redmine `GET /issues.json` の `issues[]` 要素 1 件分。
 *
 * Redmine REST はフィールドが時期 / 設定により増減するため `JsonIgnoreProperties` で
 * 未知フィールドを許容する。Domain `Issue` への変換は `IssueMapper` 経由で行う。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IssueDto(
    @JsonProperty("id") val id: Int,
    @JsonProperty("project") val project: NamedRefDto,
    @JsonProperty("tracker") val tracker: NamedRefDto,
    @JsonProperty("status") val status: NamedRefDto,
    @JsonProperty("priority") val priority: NamedRefDto,
    @JsonProperty("subject") val subject: String,
    @JsonProperty("description") val description: String? = null,
    @JsonProperty("author") val author: NamedRefDto,
    @JsonProperty("assigned_to") val assignedTo: NamedRefDto? = null,
    @JsonProperty("created_on") val createdOn: Instant,
    @JsonProperty("updated_on") val updatedOn: Instant,
    @JsonProperty("journals") val journals: List<JournalDto> = emptyList(),
)

/** `{"id": 1, "name": "..."}` 形式の参照オブジェクト。 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NamedRefDto(
    @JsonProperty("id") val id: Int,
    @JsonProperty("name") val name: String,
)
