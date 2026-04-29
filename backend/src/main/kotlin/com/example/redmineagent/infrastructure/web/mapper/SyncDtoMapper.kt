package com.example.redmineagent.infrastructure.web.mapper

import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncState
import com.example.redmineagent.infrastructure.web.dto.SyncRunDto
import com.example.redmineagent.infrastructure.web.dto.SyncStartedDto
import com.example.redmineagent.infrastructure.web.dto.SyncStatusDto

/**
 * Domain Model → REST DTO の変換 (拡張関数集約)。
 *
 * 双方向の必要はないため `toXxxDto()` のみ提供。`SyncRun.id` は permanent な永続化後の値
 * なのでここでは null 想定なし (永続化前に DTO 化することはない)。
 */

private fun requireRunId(run: SyncRun): Long = requireNotNull(run.id) { "SyncRun.id must be non-null when converting to DTO" }

fun SyncRun.toStartedDto(): SyncStartedDto =
    SyncStartedDto(
        runId = requireRunId(this),
        kind = kind.name.lowercase(),
        startedAt = startedAt,
        status = status.name.lowercase(),
    )

fun SyncRun.toRunDto(): SyncRunDto =
    SyncRunDto(
        id = requireRunId(this),
        kind = kind.name.lowercase(),
        startedAt = startedAt,
        finishedAt = finishedAt,
        status = status.name.lowercase(),
        ticketsFetched = ticketsFetched,
        chunksUpserted = chunksUpserted,
        chunksSkipped = chunksSkipped,
        ticketsDeleted = ticketsDeleted,
        errorMessage = errorMessage,
    )

fun SyncState.toStatusDto(
    currentlyRunning: Boolean,
    currentRunId: Long?,
): SyncStatusDto =
    SyncStatusDto(
        source = source,
        lastSyncStartedAt = lastSyncStartedAt,
        lastSyncFinishedAt = lastSyncFinishedAt,
        lastFullReconcileAt = lastFullReconcileAt,
        lastError = lastError,
        ticketsTotal = ticketsTotal,
        currentlyRunning = currentlyRunning,
        currentRunId = currentRunId,
    )
