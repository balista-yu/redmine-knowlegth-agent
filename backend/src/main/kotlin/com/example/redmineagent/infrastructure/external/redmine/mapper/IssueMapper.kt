package com.example.redmineagent.infrastructure.external.redmine.mapper

import com.example.redmineagent.domain.model.Issue
import com.example.redmineagent.domain.model.IssuePage
import com.example.redmineagent.domain.model.Journal
import com.example.redmineagent.infrastructure.external.redmine.dto.IssueDto
import com.example.redmineagent.infrastructure.external.redmine.dto.IssueListResponseDto
import com.example.redmineagent.infrastructure.external.redmine.dto.JournalDto

/**
 * Redmine DTO → Domain Model への変換。
 * 拡張関数で書き、双方向はサポートしない (Domain → DTO は不要なので)。
 */

fun IssueDto.toDomain(baseUrl: String): Issue =
    Issue(
        ticketId = id,
        projectId = project.id,
        projectName = project.name,
        tracker = tracker.name,
        status = status.name,
        priority = priority.name,
        subject = subject,
        author = author.name,
        assignee = assignedTo?.name,
        createdOn = createdOn,
        updatedOn = updatedOn,
        url = "$baseUrl/issues/$id",
        description = description,
        journals = journals.map { it.toDomain() },
    )

fun JournalDto.toDomain(): Journal =
    Journal(
        notes = notes,
        author = user.name,
        createdOn = createdOn,
    )

fun IssueListResponseDto.toDomain(baseUrl: String): IssuePage =
    IssuePage(
        issues = issues.map { it.toDomain(baseUrl) },
        totalCount = totalCount,
        offset = offset,
        limit = limit,
    )
