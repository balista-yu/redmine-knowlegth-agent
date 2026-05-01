package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class IssueTest :
    FunSpec({
        fun issueWith(
            ticketId: Int = 1,
            projectId: Int = 10,
            subject: String = "subject",
        ) = Issue(
            ticketId = ticketId,
            projectId = projectId,
            projectName = "proj",
            tracker = "Bug",
            status = "New",
            priority = "Normal",
            subject = subject,
            author = "alice",
            assignee = null,
            createdOn = Instant.parse("2026-01-01T00:00:00Z"),
            updatedOn = Instant.parse("2026-01-01T00:00:00Z"),
            url = "http://redmine/issues/1",
            description = null,
            journals = emptyList(),
        )

        test("ticketId が 0 以下なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { issueWith(ticketId = 0) }
            shouldThrow<IllegalArgumentException> { issueWith(ticketId = -1) }
        }

        test("projectId が 0 以下なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { issueWith(projectId = 0) }
        }

        test("subject が blank なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { issueWith(subject = " ") }
        }

        test("正常値の Issue は journals が空でも生成できる") {
            val issue = issueWith()
            issue.journals shouldBe emptyList()
        }
    })
