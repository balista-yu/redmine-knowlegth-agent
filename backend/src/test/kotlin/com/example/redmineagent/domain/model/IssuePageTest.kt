package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class IssuePageTest {
    @Test
    fun `offset が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            IssuePage(issues = emptyList(), totalCount = 0, offset = -1, limit = 100)
        }
    }

    @Test
    fun `limit が 0 以下なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            IssuePage(issues = emptyList(), totalCount = 0, offset = 0, limit = 0)
        }
    }

    @Test
    fun `totalCount が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            IssuePage(issues = emptyList(), totalCount = -1, offset = 0, limit = 100)
        }
    }
}
