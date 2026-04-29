package com.example.redmineagent.domain.model

/**
 * LLM ストリーミング応答の 1 トークン単位の差分。
 * 空文字列も発生しうる (例: 接続初期化トークン)。
 */
data class LlmDelta(
    val text: String,
)
