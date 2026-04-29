package com.example.redmineagent.domain.service

import java.security.MessageDigest

/**
 * SHA-256 を計算する純粋関数の集約。
 *
 * Domain Service として `domain/service/` に配置。I/O も状態も持たない singleton。
 * `ChunkBuilder` から `contentHash = sha256Hex(chunkType + content)` の形で利用される
 * (詳細: docs/01-design.md §3.2.1)。
 */
object HashCalculator {
    private const val ALGORITHM = "SHA-256"

    /** UTF-8 バイト列を SHA-256 でハッシュ化し、64 文字の小文字 hex 文字列で返す。 */
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        return digest.digest(input.toByteArray(Charsets.UTF_8)).toHexString()
    }
}
