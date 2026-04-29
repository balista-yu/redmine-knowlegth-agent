package com.example.redmineagent.domain.model

/**
 * AnswerQuestion ユースケースが SSE 用に発行するイベント。
 * Infrastructure 層 (`ChatController`) で SSE フレームに変換される (`docs/03-api-spec.md` §1)。
 *
 * 並び順: `Delta` ストリーム → `Sources` (1 回) → `Done` 。
 * 異常時は途中で `Error` を発行して終端する。
 */
sealed class AgentEvent {
    /** LLM 応答の 1 トークン分の delta。 */
    data class Delta(
        val text: String,
    ) : AgentEvent()

    /** RAG 検索でヒットした引用元の一覧 (回答完了直前に 1 回送出)。 */
    data class Sources(
        val hits: List<TicketHit>,
    ) : AgentEvent()

    /** 応答ストリームの正常終端。 */
    data object Done : AgentEvent()

    /** 応答ストリームの異常終端。`code` は API 仕様 §1 / §6 のエラーコード。 */
    data class Error(
        val code: String,
        val message: String,
    ) : AgentEvent()
}
