package com.example.redmineagent.infrastructure.web.dto

/**
 * `POST /api/chat` SSE の各イベント payload (詳細: docs/03-api-spec.md §1)。
 *
 * - `delta`   : `{ "text": "..." }`
 * - `sources` : `{ "items": [TicketHitDto, ...] }`
 * - `done`    : `{ "conversationId": "..." }`
 * - `error`   : `{ "code": "...", "message": "..." }`
 *
 * sealed ではなく独立した data class で定義。SSE event 名はコントローラ側で
 * `ServerSentEvent.builder().event(name)` に明示的にマッピングする。
 */

data class ChatDeltaDto(
    val text: String,
)

data class ChatSourcesDto(
    val items: List<TicketHitDto>,
)

data class ChatDoneDto(
    val conversationId: String,
)

data class ChatErrorDto(
    val code: String,
    val message: String,
)

/** API 仕様 §1 「sources」イベントの items 要素。 */
data class TicketHitDto(
    val ticketId: Int,
    val subject: String,
    val url: String,
    val snippet: String,
    val score: Float,
    val status: String,
    val tracker: String,
    val projectName: String,
)
