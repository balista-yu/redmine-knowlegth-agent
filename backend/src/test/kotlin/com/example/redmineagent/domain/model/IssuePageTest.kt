package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class IssuePageTest :
    FunSpec({
        test("offset が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                IssuePage(issues = emptyList(), totalCount = 0, offset = -1, limit = 100)
            }
        }

        test("limit が 0 以下なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                IssuePage(issues = emptyList(), totalCount = 0, offset = 0, limit = 0)
            }
        }

        test("totalCount が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> {
                IssuePage(issues = emptyList(), totalCount = -1, offset = 0, limit = 100)
            }
        }
    })
