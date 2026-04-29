package com.example.redmineagent.domain.service

import com.example.redmineagent.domain.model.ChunkType
import com.example.redmineagent.domain.model.Issue
import com.example.redmineagent.domain.model.TicketChunk

/**
 * `Issue` を `List<TicketChunk>` に変換する純粋関数。
 *
 * 動作仕様 (docs/02-tasks.md T-1-4 / docs/01-design.md §8.1):
 *  - chunkIndex=0       : description (空 / blank ならスキップ)
 *  - chunkIndex=1..N    : journals[].notes (空はスキップ、連番は空除外後)
 *  - 1 チャンク超過時    : MAX_CHUNK_LENGTH (1500) で再分割し subIndex を付与
 *                         境界は改行優先。改行が無ければ文字数で fallback
 *                         隣接チャンクは CHUNK_OVERLAP (50) 文字オーバーラップ
 *  - 各チャンク入力     : MAX_INPUT_LENGTH (8000) で先頭からトリム
 *  - contentHash        : sha256(chunkType.lowercase() + content)
 */
object ChunkBuilder {
    private const val MAX_CHUNK_LENGTH = 1500
    private const val CHUNK_OVERLAP = 50
    private const val MAX_INPUT_LENGTH = 8000

    fun build(issue: Issue): List<TicketChunk> {
        val result = mutableListOf<TicketChunk>()

        // description は chunkIndex = 0 固定
        val description = issue.description?.take(MAX_INPUT_LENGTH).orEmpty()
        if (description.isNotBlank()) {
            result += toChunks(issue.ticketId, ChunkType.DESCRIPTION, chunkIndex = 0, body = description)
        }

        // journals は空 (null / blank) を除外したうえで chunkIndex = 1..N
        var journalIndex = 0
        for (journal in issue.journals) {
            val notes = journal.notes?.take(MAX_INPUT_LENGTH).orEmpty()
            if (notes.isBlank()) continue
            journalIndex++
            result += toChunks(issue.ticketId, ChunkType.JOURNAL, chunkIndex = journalIndex, body = notes)
        }

        return result
    }

    private fun toChunks(
        ticketId: Int,
        chunkType: ChunkType,
        chunkIndex: Int,
        body: String,
    ): List<TicketChunk> =
        splitWithOverlap(body, MAX_CHUNK_LENGTH, CHUNK_OVERLAP)
            .filter { it.isNotBlank() }
            .mapIndexed { sub, segment ->
                TicketChunk(
                    ticketId = ticketId,
                    chunkType = chunkType,
                    chunkIndex = chunkIndex,
                    subIndex = sub,
                    content = segment,
                    contentHash = HashCalculator.sha256Hex(chunkType.name.lowercase() + segment),
                )
            }

    /**
     * `text` を最大 `maxLen` の chunk に分割する。
     *
     * 各 chunk の境界は、可能なら `\n` の位置で切る (改行優先)。改行が無ければ
     * 単純に `maxLen` 文字で fallback する。隣接 chunk は `overlap` 文字分の
     * オーバーラップを持つ (連続性確保)。
     *
     * `text.length <= maxLen` の場合は分割せず単一要素のリストを返す。
     */
    private fun splitWithOverlap(
        text: String,
        maxLen: Int,
        overlap: Int,
    ): List<String> {
        if (text.length <= maxLen) return listOf(text)

        val out = mutableListOf<String>()
        var start = 0
        while (start < text.length) {
            val tentativeEnd = (start + maxLen).coerceAtMost(text.length)
            val end =
                if (tentativeEnd == text.length) {
                    tentativeEnd
                } else {
                    val newlineIndex = text.lastIndexOf('\n', tentativeEnd - 1)
                    if (newlineIndex > start) newlineIndex + 1 else tentativeEnd
                }
            out += text.substring(start, end)
            if (end == text.length) break
            // 必ず前進させる: overlap 分戻すが、最低 1 文字進む
            start = (end - overlap).coerceAtLeast(start + 1)
        }
        return out
    }
}
