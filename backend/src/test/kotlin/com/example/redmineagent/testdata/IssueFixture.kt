package com.example.redmineagent.testdata

import com.example.redmineagent.domain.model.Issue
import com.example.redmineagent.domain.model.Journal
import java.time.Instant

/**
 * Issue / Journal のテストデータビルダー (.claude/rules/testing.md 「テストデータビルダー」)。
 *
 * 各テストでは `IssueFixture.issue(description=..., journals=...)` のように必要な
 * 引数だけ override する。
 */
object IssueFixture {
    private val FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z")

    fun issue(
        ticketId: Int = 1,
        description: String? = null,
        journals: List<Journal> = emptyList(),
    ): Issue =
        Issue(
            ticketId = ticketId,
            projectId = 100,
            projectName = "proj",
            tracker = "Bug",
            status = "New",
            priority = "Normal",
            subject = "subject",
            author = "alice",
            assignee = null,
            createdOn = FIXED_INSTANT,
            updatedOn = FIXED_INSTANT,
            url = "http://redmine.example/issues/$ticketId",
            description = description,
            journals = journals,
        )

    fun journal(
        notes: String?,
        author: String = "bob",
    ): Journal =
        Journal(
            notes = notes,
            author = author,
            createdOn = FIXED_INSTANT,
        )
}
