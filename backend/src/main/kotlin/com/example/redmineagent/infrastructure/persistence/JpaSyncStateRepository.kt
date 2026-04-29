package com.example.redmineagent.infrastructure.persistence

import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import com.example.redmineagent.domain.model.SyncState
import com.example.redmineagent.domain.repository.SyncStateRepository
import com.example.redmineagent.infrastructure.persistence.entity.SyncRunEntity
import com.example.redmineagent.infrastructure.persistence.jpa.SyncRunJpaRepository
import com.example.redmineagent.infrastructure.persistence.jpa.SyncStateJpaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * `SyncStateRepository` の Spring Data JPA 実装。
 *
 * - JPA の同期 API を `withContext(Dispatchers.IO)` で逃がす
 * - 書き込み系は `@Transactional` で境界を切る (T-1-9 動作要件)
 * - source は 'redmine' 固定 (V2__seed_sync_state.sql で 1 行 seed 済み)
 */
@Repository
@Lazy
class JpaSyncStateRepository(
    private val syncStateJpa: SyncStateJpaRepository,
    private val syncRunJpa: SyncRunJpaRepository,
) : SyncStateRepository {
    @Transactional(readOnly = true)
    override suspend fun load(): SyncState =
        withContext(Dispatchers.IO) {
            val entity =
                syncStateJpa.findBySource(SOURCE_REDMINE)
                    ?: error("sync_state row for source='$SOURCE_REDMINE' not found")
            entity.toDomain()
        }

    @Transactional
    override suspend fun updateLastSyncStartedAt(at: Instant) {
        withContext(Dispatchers.IO) {
            mutateState { it.lastSyncStartedAt = at }
        }
    }

    @Transactional
    override suspend fun updateLastFullReconcileAt(at: Instant) {
        withContext(Dispatchers.IO) {
            mutateState { it.lastFullReconcileAt = at }
        }
    }

    @Transactional
    override suspend fun updateTicketsTotal(total: Int) {
        withContext(Dispatchers.IO) {
            mutateState { it.ticketsTotal = total }
        }
    }

    @Transactional
    override suspend fun updateLastError(message: String?) {
        withContext(Dispatchers.IO) {
            mutateState { it.lastError = message }
        }
    }

    @Transactional
    override suspend fun startRun(
        kind: SyncRunKind,
        startedAt: Instant,
    ): SyncRun =
        withContext(Dispatchers.IO) {
            val entity =
                SyncRunEntity(
                    kind = kind.name.lowercase(),
                    startedAt = startedAt,
                    status = SyncRunStatus.RUNNING.name.lowercase(),
                )
            syncRunJpa.save(entity).toDomain()
        }

    @Transactional
    override suspend fun completeRun(run: SyncRun): SyncRun =
        withContext(Dispatchers.IO) {
            val id = requireNotNull(run.id) { "completeRun requires SyncRun.id" }
            val entity =
                syncRunJpa.findById(id).orElseThrow {
                    error("sync_run id=$id not found")
                }
            entity.finishedAt = run.finishedAt
            entity.ticketsFetched = run.ticketsFetched
            entity.chunksUpserted = run.chunksUpserted
            entity.chunksSkipped = run.chunksSkipped
            entity.ticketsDeleted = run.ticketsDeleted
            entity.status = run.status.name.lowercase()
            entity.errorMessage = run.errorMessage
            syncRunJpa.save(entity).toDomain()
        }

    @Transactional
    override suspend fun failRun(run: SyncRun): SyncRun = completeRun(run)

    @Transactional(readOnly = true)
    override suspend fun listRecentRuns(
        limit: Int,
        kind: SyncRunKind?,
    ): List<SyncRun> =
        withContext(Dispatchers.IO) {
            val pageable = PageRequest.of(0, limit)
            val entities =
                if (kind == null) {
                    syncRunJpa.findAllByOrderByStartedAtDesc(pageable)
                } else {
                    syncRunJpa.findByKindOrderByStartedAtDesc(kind.name.lowercase(), pageable)
                }
            entities.map { it.toDomain() }
        }

    private fun mutateState(mutator: (com.example.redmineagent.infrastructure.persistence.entity.SyncStateEntity) -> Unit) {
        val entity =
            syncStateJpa.findBySource(SOURCE_REDMINE)
                ?: error("sync_state row for source='$SOURCE_REDMINE' not found")
        mutator(entity)
        syncStateJpa.save(entity)
    }

    companion object {
        private const val SOURCE_REDMINE = "redmine"
    }
}
