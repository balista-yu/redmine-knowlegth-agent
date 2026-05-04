package com.example.redmineagent.application.exception

/**
 * Ollama に接続できない/応答異常などの「サービス断」を表す Application 層例外。
 *
 * Infrastructure 層 (OllamaEmbeddingService, KoogChatModel) で SDK / WebClient の
 * connection / 5xx 系エラーを検出した際に変換して投げる。
 *
 * `AnswerQuestionApplicationService` がこの型で `is` 判定し
 * `AgentEvent.Error(code = "OLLAMA_UNAVAILABLE")` に対応付ける
 * (詳細: docs/03-api-spec.md §6, §1)。
 */
class OllamaUnavailableException(
    message: String = "Ollama is unavailable",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
