package com.example.redmineagent.infrastructure.external.redmine.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * `GET /issues.json` のレスポンス全体構造。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class IssueListResponseDto(
    @JsonProperty("issues") val issues: List<IssueDto>,
    @JsonProperty("total_count") val totalCount: Int,
    @JsonProperty("offset") val offset: Int,
    @JsonProperty("limit") val limit: Int,
)
