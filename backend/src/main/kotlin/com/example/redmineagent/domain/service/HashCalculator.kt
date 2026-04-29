package com.example.redmineagent.domain.service

import java.security.MessageDigest

/**
 * SHA-256 を計算する純粋関数の集約。
 *
 * Domain Service として `domain/service/` に配置。I/O も状態も持たない。
 * `ChunkBuilder` から `contentHash = sha256Hex(chunkType + content)` の形で利用される
 * (詳細: docs/01-design.md §3.2.1)。
 */
class HashCalculator {
    /** UTF-8 バイト列を SHA-256 でハッシュ化し、64 文字の小文字 hex 文字列で返す。 */
    fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance(ALGORITHM)
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.toHexString()
    }

    private fun ByteArray.toHexString(): String =
        buildString(size * HEX_CHARS_PER_BYTE) {
            for (b in this@toHexString) {
                append(HEX_DIGITS[(b.toInt() ushr HEX_HIGH_NIBBLE_SHIFT) and HEX_NIBBLE_MASK])
                append(HEX_DIGITS[b.toInt() and HEX_NIBBLE_MASK])
            }
        }

    companion object {
        private const val ALGORITHM = "SHA-256"
        private const val HEX_CHARS_PER_BYTE = 2
        private const val HEX_HIGH_NIBBLE_SHIFT = 4
        private const val HEX_NIBBLE_MASK = 0x0f
        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
    }
}
