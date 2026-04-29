package com.example.redmineagent.application.service

import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import com.example.redmineagent.domain.model.SyncStatusSnapshot
import com.example.redmineagent.domain.repository.SyncStateRepository
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service

/**
 * 同期状態の取得 (F-07: GET /api/sync/status, GET /api/sync/runs) のユースケース。
 *
 * Controller が `SyncStateRepository` を直接呼ばないようにするための薄い橋渡し
 * (`infrastructure.web` から `domain.repository` への直接依存を ArchUnit ルール 3 が禁止)。
 */
@Service
@Lazy
class GetSyncStatusApplicationService(
    private val syncStateRepository: SyncStateRepository,
) {
    suspend fun getStatus(): SyncStatusSnapshot {
        val state = syncStateRepository.load()
        val recent = syncStateRepository.listRecentRuns(1, kind = null).firstOrNull()
        val running = recent != null && recent.status == SyncRunStatus.RUNNING
        val currentRunId = if (running) recent?.id else null
        return SyncStatusSnapshot(
            state = state,
            currentlyRunning = running,
            currentRunId = currentRunId,
        )
    }

    suspend fun listRecentRuns(
        limit: Int,
        kind: SyncRunKind?,
    ): List<SyncRun> = syncStateRepository.listRecentRuns(limit, kind)
}
