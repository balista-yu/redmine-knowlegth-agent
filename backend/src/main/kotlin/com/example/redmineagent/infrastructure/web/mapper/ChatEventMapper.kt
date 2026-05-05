package com.example.redmineagent.infrastructure.web.mapper

import com.example.redmineagent.domain.model.AgentEvent
import com.example.redmineagent.domain.model.TicketHit
import com.example.redmineagent.infrastructure.web.dto.ChatDeltaDto
import com.example.redmineagent.infrastructure.web.dto.ChatDoneDto
import com.example.redmineagent.infrastructure.web.dto.ChatErrorDto
import com.example.redmineagent.infrastructure.web.dto.ChatSourcesDto
import com.example.redmineagent.infrastructure.web.dto.TicketHitDto
import org.springframework.http.codec.ServerSentEvent

/**
 * Domain `AgentEvent` → SSE フレーム (`ServerSentEvent`) の変換ロジック。
 *
 * SSE イベント名:
 *  - `delta` / `sources` / `done` / `error`
 *
 * `done` イベントには `conversationId` を含めるため、コントローラから値を渡す。
 */

fun TicketHit.toDto(): TicketHitDto =
    TicketHitDto(
        ticketId = ticketId,
        subject = subject,
        url = url,
        snippet = snippet,
        score = score,
        status = status,
        tracker = tracker,
        projectName = projectName,
    )

fun AgentEvent.toServerSentEvent(conversationId: String): ServerSentEvent<Any> =
    when (this) {
        is AgentEvent.Delta -> {
            ServerSentEvent
                .builder<Any>(ChatDeltaDto(text = text))
                .event(EVENT_DELTA)
                .build()
        }

        is AgentEvent.Sources -> {
            ServerSentEvent
                .builder<Any>(ChatSourcesDto(items = hits.map { it.toDto() }))
                .event(EVENT_SOURCES)
                .build()
        }

        AgentEvent.Done -> {
            ServerSentEvent
                .builder<Any>(ChatDoneDto(conversationId = conversationId))
                .event(EVENT_DONE)
                .build()
        }

        is AgentEvent.Error -> {
            ServerSentEvent
                .builder<Any>(ChatErrorDto(code = code, message = message))
                .event(EVENT_ERROR)
                .build()
        }
    }

private const val EVENT_DELTA = "delta"
private const val EVENT_SOURCES = "sources"
private const val EVENT_DONE = "done"
private const val EVENT_ERROR = "error"
