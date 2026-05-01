package com.example.redmineagent.application.service

import com.example.redmineagent.application.exception.EmbeddingTooLongException
import com.example.redmineagent.application.exception.SyncAlreadyRunningException
import com.example.redmineagent.domain.gateway.EmbeddingService
import com.example.redmineagent.domain.gateway.RedmineGateway
import com.example.redmineagent.domain.model.Issue
import com.example.redmineagent.domain.model.SyncMode
import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunStatus
import com.example.redmineagent.domain.model.TicketChunk
import com.example.redmineagent.domain.model.TicketChunkVector
import com.example.redmineagent.domain.model.TicketMetadata
import com.example.redmineagent.domain.repository.SyncStateRepository
import com.example.redmineagent.domain.repository.TicketChunkRepository
import com.example.redmineagent.domain.service.ChunkBuilder
import com.example.redmineagent.domain.service.HashCalculator
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * F-01 (差分同期) ユースケース実装。
 *
 * フロー (詳細: docs/01-design.md §5.1):
 *  1. `SyncStateRepository.load()` で前回同期時刻を取得
 *  2. `startRun(kind, startedAt)` で `sync_run` に running を記録
 *  3. Redmine をページングで取得 → Issue ごとに:
 *     a. `ChunkBuilder.build(issue)` で TicketChunk 列を生成
 *     b. `findByTicketId` で既存チャンクを取得し、contentHash 一致なら skip
 *     c. 不一致のチャンクのみ `EmbeddingService.embed` → `upsert`
 *     d. journal 縮小に備えて `deleteOrphanChunks(ticketId, validChunks)`
 *  4. 完了後: `completeRun(success)` + `updateLastSyncStartedAt(thisRun.startedAt)`
 *
 * エラーハンドリング (docs/01-design.md §8.5):
 *  - 同期走行中の二重呼び出し → `SyncAlreadyRunningException`
 *  - Redmine 5xx / Qdrant エラーで page 取得が失敗 → `failRun` + `last_error` 更新
 *  - 単一 Issue 処理失敗はループを止めず、エラー集計のみ (件数は failedTickets としてログ)
 *  - `EmbeddingTooLongException` → 当該チャンクを半分分割して再試行 (最大 2 階層 = 計 4 分割)、
 *    それでも超過する場合はそのチャンクをスキップ
 */
@Service
@Lazy
class SyncIssuesApplicationService(
    private val redmineGateway: RedmineGateway,
    private val ticketChunkRepository: TicketChunkRepository,
    private val embeddingService: EmbeddingService,
    private val syncStateRepository: SyncStateRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val running = AtomicBoolean(false)

    suspend fun execute(mode: SyncMode = SyncMode.INCREMENTAL): SyncRun {
        if (!running.compareAndSet(false, true)) {
            throw SyncAlreadyRunningException()
        }
        val startedAt = Instant.now()
        return try {
            runSync(mode, startedAt)
        } finally {
            running.set(false)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Redmine/Qdrant 何が来ても sync_run を failed に記録したい
    private suspend fun runSync(
        mode: SyncMode,
        startedAt: Instant,
    ): SyncRun {
        val state = syncStateRepository.load()
        val since = if (mode == SyncMode.FULL) null else state.lastSyncStartedAt
        val runRecord = syncStateRepository.startRun(mode.toRunKind(), startedAt)
        logger.info("Sync started: mode={}, runId={}, since={}", mode, runRecord.id, since)

        return try {
            val counts = processAllPages(since)
            val finished =
                runRecord.copy(
                    finishedAt = Instant.now(),
                    ticketsFetched = counts.ticketsFetched,
                    chunksUpserted = counts.chunksUpserted,
                    chunksSkipped = counts.chunksSkipped,
                    ticketsDeleted = counts.ticketsDeleted,
                    status = SyncRunStatus.SUCCESS,
                    errorMessage = null,
                )
            val saved = syncStateRepository.completeRun(finished)
            syncStateRepository.updateLastSyncStartedAt(startedAt)
            syncStateRepository.updateLastError(null)
            logger.info(
                "Sync completed: runId={}, fetched={}, upserted={}, skipped={}, orphansDeleted={}",
                saved.id,
                counts.ticketsFetched,
                counts.chunksUpserted,
                counts.chunksSkipped,
                counts.ticketsDeleted,
            )
            saved
        } catch (e: RuntimeException) {
            // Redmine / Qdrant のページ取得段で失敗 → 全体 failed として記録 (DB 更新自体は別 try)
            logger.error("Sync failed: runId={}, error={}", runRecord.id, e.message, e)
            val failed =
                runRecord.copy(
                    finishedAt = Instant.now(),
                    status = SyncRunStatus.FAILED,
                    errorMessage = e.message ?: e::class.java.simpleName,
                )
            syncStateRepository.failRun(failed)
            syncStateRepository.updateLastError(failed.errorMessage)
            throw e
        }
    }

    private suspend fun processAllPages(since: Instant?): RunCounts {
        val counts = RunCounts.zero()
        var offset = 0
        while (true) {
            val page = redmineGateway.listIssuesUpdatedSince(since, offset, PAGE_SIZE)
            for (issue in page.issues) {
                processIssueSafely(issue, counts)
            }
            offset += page.issues.size
            // 全件取得完了 (これ以上ページが無い、または取得 0 件で前進不能)
            if (page.issues.isEmpty() || offset >= page.totalCount) break
        }
        return counts
    }

    @Suppress("TooGenericExceptionCaught") // 単一 Issue で何が起きてもループ全体は止めない方針
    private suspend fun processIssueSafely(
        issue: Issue,
        counts: RunCounts,
    ) {
        try {
            processIssue(issue, counts)
            counts.ticketsFetched += 1
        } catch (e: RuntimeException) {
            // 単一 Issue 失敗はループを止めない (docs/02-tasks.md T-1-10 動作仕様)
            logger.warn(
                "Issue processing failed, continuing: ticketId={}, error={}",
                issue.ticketId,
                e.message,
                e,
            )
        }
    }

    private suspend fun processIssue(
        issue: Issue,
        counts: RunCounts,
    ) {
        val newChunks = ChunkBuilder.build(issue)
        val metadata = TicketMetadata.fromIssue(issue)
        val existing = ticketChunkRepository.findByTicketId(issue.ticketId)
        val existingHashByKey =
            existing.associateBy { ChunkKey(it.chunkType, it.chunkIndex, it.subIndex) }

        val toEmbed = mutableListOf<TicketChunk>()
        for (chunk in newChunks) {
            val key = ChunkKey(chunk.chunkType, chunk.chunkIndex, chunk.subIndex)
            val prev = existingHashByKey[key]
            if (prev != null && prev.contentHash == chunk.contentHash) {
                counts.chunksSkipped += 1
            } else {
                toEmbed += chunk
            }
        }

        if (toEmbed.isNotEmpty()) {
            val vectors = mutableListOf<TicketChunkVector>()
            for (chunk in toEmbed) {
                vectors += embedWithSplitOnOverflow(chunk, metadata, depth = 0)
            }
            if (vectors.isNotEmpty()) {
                ticketChunkRepository.upsert(vectors)
                counts.chunksUpserted += vectors.size
            }
        }

        // journal 縮小 / 文字数減少で残った旧チャンクを掃除
        val orphans = ticketChunkRepository.deleteOrphanChunks(issue.ticketId, newChunks)
        counts.ticketsDeleted += orphans
    }

    /**
     * `EmbeddingTooLongException` 発生時に content を半分にして再帰的に embed する。
     * 最大階層 `MAX_SPLIT_DEPTH` (= 2) まで。それでも超過する場合は当該チャンクをスキップ。
     */
    private suspend fun embedWithSplitOnOverflow(
        chunk: TicketChunk,
        metadata: TicketMetadata,
        depth: Int,
    ): List<TicketChunkVector> =
        try {
            val vector = embeddingService.embed(chunk.content)
            listOf(TicketChunkVector(chunk, vector, metadata))
        } catch (e: EmbeddingTooLongException) {
            if (depth >= MAX_SPLIT_DEPTH) {
                logger.warn(
                    "Embedding still too long after {} splits, skipping: ticketId={}, chunkIndex={}, subIndex={}, error={}",
                    MAX_SPLIT_DEPTH,
                    chunk.ticketId,
                    chunk.chunkIndex,
                    chunk.subIndex,
                    e.message,
                )
                emptyList()
            } else {
                splitInHalf(chunk).flatMap { embedWithSplitOnOverflow(it, metadata, depth + 1) }
            }
        }

    /**
     * 1 つの TicketChunk を content 中央で 2 つに切り分け、新たな subIndex を割り当てる。
     * subIndex 衝突を避けるため `subIndex * 2` / `subIndex * 2 + 1` を使う (隣接チャンクとの
     * 干渉は MAX_SPLIT_DEPTH=2 + ChunkBuilder の連番性の下では発生しない範囲に収まる)。
     */
    private fun splitInHalf(chunk: TicketChunk): List<TicketChunk> {
        val mid = chunk.content.length / 2
        if (mid == 0) return emptyList()
        val firstHalf = chunk.content.substring(0, mid)
        val secondHalf = chunk.content.substring(mid)
        return listOf(
            chunkOf(chunk, firstHalf, chunk.subIndex * 2),
            chunkOf(chunk, secondHalf, chunk.subIndex * 2 + 1),
        )
    }

    private fun chunkOf(
        original: TicketChunk,
        body: String,
        newSubIndex: Int,
    ): TicketChunk =
        TicketChunk(
            ticketId = original.ticketId,
            chunkType = original.chunkType,
            chunkIndex = original.chunkIndex,
            subIndex = newSubIndex,
            content = body,
            contentHash = HashCalculator.sha256Hex(original.chunkType.name.lowercase() + body),
        )

    private data class ChunkKey(
        val chunkType: com.example.redmineagent.domain.model.ChunkType,
        val chunkIndex: Int,
        val subIndex: Int,
    )

    private class RunCounts(
        var ticketsFetched: Int,
        var chunksUpserted: Int,
        var chunksSkipped: Int,
        var ticketsDeleted: Int,
    ) {
        companion object {
            fun zero() = RunCounts(0, 0, 0, 0)
        }
    }

    companion object {
        private const val PAGE_SIZE = 100
        private const val MAX_SPLIT_DEPTH = 2
    }
}
