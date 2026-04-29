package com.example.redmineagent.domain.model

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class TicketHitTest {
    @Test
    fun `ticketId が 0 以下なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            hitWith(ticketId = 0)
        }
    }

    @Test
    fun `score が負なら IllegalArgumentException`() {
        shouldThrow<IllegalArgumentException> {
            hitWith(score = -0.01f)
        }
    }

    private fun hitWith(
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
}
