package com.example.redmineagent.application.service

import com.example.redmineagent.application.exception.SyncAlreadyRunningException
import com.example.redmineagent.domain.gateway.RedmineGateway
import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import com.example.redmineagent.domain.repository.SyncStateRepository
import com.example.redmineagent.domain.repository.TicketChunkRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

/**
 * F-03 (Reconciliation) ユースケース実装。
 *
 * フロー (詳細: docs/01-design.md §5.3):
 *  1. Redmine から ID 一覧を全件取得 (`RedmineGateway.listAllIssueIds`)
 *  2. Qdrant 側の `ticket_id` 集合を取得 (`TicketChunkRepository.listAllTicketIds`)
 *  3. 差集合 (Qdrant - Redmine) を `deleteByTicketId` で逐次削除
 *  4. `completeRun(success)` + `updateLastFullReconcileAt` + `updateTicketsTotal`
 *
 * エラーハンドリング:
 *  - 走行中の二重呼び出し → `SyncAlreadyRunningException`
 *  - Redmine 取得失敗 → `failRun` + `last_error` 更新 + 例外伝播
 *  - 単一 ticket の削除失敗はループを止めず warn ログを残す
 *
 * SyncIssuesApplicationService と AtomicBoolean は共有しない (別ユースケース、Qdrant 側の
 * `deleteByTicketId` 単位の原子性で十分なため、同時走行を強制禁止しない方針)。
 */
@Service
@Lazy
class ReconcileApplicationService(
    private val redmineGateway: RedmineGateway,
    private val ticketChunkRepository: TicketChunkRepository,
    private val syncStateRepository: SyncStateRepository,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val running = AtomicBoolean(false)

    suspend fun execute(): SyncRun {
        if (!running.compareAndSet(false, true)) {
            throw SyncAlreadyRunningException("Reconcile is already running")
        }
        val startedAt = Instant.now()
        return try {
            runReconcile(startedAt)
        } finally {
            running.set(false)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Redmine/Qdrant 何が来ても sync_run を failed に記録したい
    private suspend fun runReconcile(startedAt: Instant): SyncRun {
        val runRecord = syncStateRepository.startRun(SyncRunKind.RECONCILE, startedAt)
        logger.info("Reconcile started: runId={}", runRecord.id)

        return try {
            val redmineIds = redmineGateway.listAllIssueIds()
            val qdrantIds = ticketChunkRepository.listAllTicketIds()
            val orphanIds = qdrantIds - redmineIds
            val deleted = deleteOrphans(orphanIds)

            val finished =
                runRecord.copy(
                    finishedAt = Instant.now(),
                    ticketsFetched = redmineIds.size,
                    ticketsDeleted = deleted,
                    status = SyncRunStatus.SUCCESS,
                    errorMessage = null,
                )
            val saved = syncStateRepository.completeRun(finished)
            syncStateRepository.updateLastFullReconcileAt(startedAt)
            syncStateRepository.updateTicketsTotal(redmineIds.size)
            syncStateRepository.updateLastError(null)
            logger.info(
                "Reconcile completed: runId={}, redmineIds={}, qdrantIds={}, deleted={}",
                saved.id,
                redmineIds.size,
                qdrantIds.size,
                deleted,
            )
            saved
        } catch (e: RuntimeException) {
            logger.error("Reconcile failed: runId={}, error={}", runRecord.id, e.message, e)
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

    @Suppress("TooGenericExceptionCaught") // 単一 ticket 削除失敗で全体を止めない方針
    private suspend fun deleteOrphans(orphanIds: Set<Int>): Int {
        var deleted = 0
        for (ticketId in orphanIds) {
            try {
                ticketChunkRepository.deleteByTicketId(ticketId)
                deleted += 1
            } catch (e: RuntimeException) {
                logger.warn("Failed to delete orphan ticket, continuing: ticketId={}, error={}", ticketId, e.message, e)
            }
        }
        return deleted
    }
}
