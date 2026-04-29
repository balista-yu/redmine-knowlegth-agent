package com.example.redmineagent.infrastructure.external.qdrant

import com.example.redmineagent.domain.model.TicketChunk
import java.security.MessageDigest
import java.util.UUID

/**
 * Qdrant Point ID を決定論的に算出する (UUID v5 / SHA-1 namespace based)。
 *
 * 同じ position (ticketId / chunkIndex / subIndex) に対しては再同期しても同じ ID が
 * 算出される。これにより upsert で自然に上書き = 差分管理が成立する
 * (詳細: docs/01-design.md §8.2)。
 *
 * Name format: `redmine:issue:{ticketId}:chunk:{chunkIndex}:{subIndex}`
 *
 * 注: 設計書 §4.1 では `[:{sub_index}]` (条件付き) と書かれているが、本実装では
 * 常に subIndex を含める形に統一した。条件分岐を避けて ID 安定性を確保するため。
 */
object PointIdGenerator {
    /** namespace = UUID.nameUUIDFromBytes("redmine-knowledge-agent") (v3 だが namespace 用途では問題ない)。 */
    private val APP_NAMESPACE: UUID = UUID.nameUUIDFromBytes(APP_NS_NAME.toByteArray(Charsets.UTF_8))

    fun pointId(chunk: TicketChunk): UUID = pointId(chunk.ticketId, chunk.chunkIndex, chunk.subIndex)

    fun pointId(
        ticketId: Int,
        chunkIndex: Int,
        subIndex: Int,
    ): UUID {
        val name = "redmine:issue:$ticketId:chunk:$chunkIndex:$subIndex"
        return uuidV5(APP_NAMESPACE, name)
    }

    private fun uuidV5(
        namespace: UUID,
        name: String,
    ): UUID {
        val sha1 = MessageDigest.getInstance("SHA-1")
        sha1.update(uuidToBytes(namespace))
        sha1.update(name.toByteArray(Charsets.UTF_8))
        val hash = sha1.digest()
        // version (5) と variant (RFC 4122) を埋め込む
        hash[VERSION_BYTE_INDEX] = ((hash[VERSION_BYTE_INDEX].toInt() and LOW_NIBBLE_MASK) or VERSION_5_FLAG).toByte()
        hash[VARIANT_BYTE_INDEX] = ((hash[VARIANT_BYTE_INDEX].toInt() and VARIANT_LOW_MASK) or VARIANT_RFC_FLAG).toByte()
        var msb = 0L
        var lsb = 0L
        for (i in 0 until BYTES_PER_LONG) {
            msb = (msb shl BITS_PER_BYTE) or (hash[i].toLong() and BYTE_MASK)
        }
        for (i in 0 until BYTES_PER_LONG) {
            lsb = (lsb shl BITS_PER_BYTE) or (hash[i + BYTES_PER_LONG].toLong() and BYTE_MASK)
        }
        return UUID(msb, lsb)
    }

    private fun uuidToBytes(uuid: UUID): ByteArray {
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        val buf = ByteArray(UUID_BYTES)
        for (i in 0 until BYTES_PER_LONG) {
            buf[i] = (msb shr (BITS_PER_BYTE * (BYTES_PER_LONG - 1 - i))).toByte()
        }
        for (i in 0 until BYTES_PER_LONG) {
            buf[i + BYTES_PER_LONG] = (lsb shr (BITS_PER_BYTE * (BYTES_PER_LONG - 1 - i))).toByte()
        }
        return buf
    }

    private const val APP_NS_NAME = "redmine-knowledge-agent"
    private const val UUID_BYTES = 16
    private const val BYTES_PER_LONG = 8
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xffL
    private const val VERSION_BYTE_INDEX = 6
    private const val VARIANT_BYTE_INDEX = 8
    private const val LOW_NIBBLE_MASK = 0x0f
    private const val VERSION_5_FLAG = 0x50
    private const val VARIANT_LOW_MASK = 0x3f
    private const val VARIANT_RFC_FLAG = 0x80
}
