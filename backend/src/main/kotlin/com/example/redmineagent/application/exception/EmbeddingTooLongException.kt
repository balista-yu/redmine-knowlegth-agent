package com.example.redmineagent.application.exception

/**
 * Embedding 入力が context length を超えた場合に Infrastructure 層から投げられる例外。
 *
 * Application 層 (`SyncIssuesApplicationService`) は当該チャンクを半分に分割して
 * 再試行する (`docs/01-design.md` §8.5)。本例外を catch するのは Application のみで、
 * Domain 層には流れない。
 */
class EmbeddingTooLongException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
