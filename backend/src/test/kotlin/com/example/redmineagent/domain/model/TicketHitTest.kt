package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec

class TicketHitTest :
    FunSpec({
        fun hitWith(
            ticketId: Int = 1,
            score: Float = 0.95f,
        ) = TicketHit(
            ticketId = ticketId,
            projectName = "proj",
            tracker = "Bug",
            status = "New",
            subject = "subject",
            url = "http://redmine/issues/1",
            score = score,
            snippet = "snippet",
        )

        test("ticketId が 0 以下なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { hitWith(ticketId = 0) }
        }

        test("score が負なら IllegalArgumentException") {
            shouldThrow<IllegalArgumentException> { hitWith(score = -0.01f) }
        }
    })
