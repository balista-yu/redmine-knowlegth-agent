package com.example.redmineagent.application.exception

/**
 * Qdrant に接続できない/応答異常などの「サービス断」を表す Application 層例外。
 *
 * Infrastructure 層 (QdrantTicketChunkRepository) で gRPC / SDK の connection エラーを
 * 検出した際に変換して投げる。
 *
 * `AnswerQuestionApplicationService` がこの型で `is` 判定し
 * `AgentEvent.Error(code = "QDRANT_UNAVAILABLE")` に対応付ける。
 */
class QdrantUnavailableException(
    message: String = "Qdrant is unavailable",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
