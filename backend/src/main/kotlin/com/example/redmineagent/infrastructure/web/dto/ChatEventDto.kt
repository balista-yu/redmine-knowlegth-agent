package com.example.redmineagent.infrastructure.web.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * `POST /api/chat` SSE の各イベント payload。
 *
 * - `delta`   : `{ "text": "..." }`
 * - `sources` : `{ "items": [TicketHitDto, ...] }`
 * - `done`    : `{ "conversationId": "..." }`
 * - `error`   : `{ "code": "...", "message": "..." }`
 *
 * sealed ではなく独立した data class で定義。SSE event 名はコントローラ側で
 * `ServerSentEvent.builder().event(name)` に明示的にマッピングする。
 */

@Schema(name = "ChatDelta", description = "SSE event=delta の payload。LLM のトークン断片")
data class ChatDeltaDto(
    @field:Schema(description = "回答テキストの断片", example = "過去のチケット #128 では...")
    val text: String,
)

@Schema(name = "ChatSources", description = "SSE event=sources の payload。引用元チケット一覧")
data class ChatSourcesDto(
    @field:Schema(description = "引用元チケットの配列")
    val items: List<TicketHitDto>,
)

@Schema(name = "ChatDone", description = "SSE event=done の payload。正常終了")
data class ChatDoneDto(
    @field:Schema(description = "会話単位識別子 (採番済み)", example = "c-2024-04-26-001")
    val conversationId: String,
)

@Schema(name = "ChatError", description = "SSE event=error の payload。異常終了")
data class ChatErrorDto(
    @field:Schema(description = "エラーコード", example = "OLLAMA_UNAVAILABLE")
    val code: String,
    @field:Schema(description = "ユーザー向けメッセージ", example = "Ollama に接続できませんでした")
    val message: String,
)

/** API `sources` イベントの items 要素。 */
@Schema(name = "TicketHit", description = "RAG ヒットチケット 1 件")
data class TicketHitDto(
    @field:Schema(description = "Redmine チケット ID", example = "128")
    val ticketId: Int,
    @field:Schema(description = "チケット件名", example = "本番Webで証明書期限切れ")
    val subject: String,
    @field:Schema(description = "Redmine 上のチケット URL", example = "http://localhost:3000/issues/128")
    val url: String,
    @field:Schema(description = "ヒットしたチャンクの抜粋 (〜200 文字)")
    val snippet: String,
    @field:Schema(description = "コサイン類似度 (0.0〜1.0)", example = "0.83")
    val score: Float,
    @field:Schema(description = "ステータス名", example = "Closed")
    val status: String,
    @field:Schema(description = "トラッカー名", example = "Bug")
    val tracker: String,
    @field:Schema(description = "プロジェクト名", example = "infra")
    val projectName: String,
)
