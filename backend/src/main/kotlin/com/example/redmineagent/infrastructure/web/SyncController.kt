package com.example.redmineagent.infrastructure.web

import com.example.redmineagent.application.service.GetSyncStatusApplicationService
import com.example.redmineagent.application.service.ReconcileApplicationService
import com.example.redmineagent.application.service.SyncIssuesApplicationService
import com.example.redmineagent.domain.model.SyncMode
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.infrastructure.web.dto.SyncRunsResponseDto
import com.example.redmineagent.infrastructure.web.dto.SyncStartedDto
import com.example.redmineagent.infrastructure.web.dto.SyncStatusDto
import com.example.redmineagent.infrastructure.web.mapper.toRunDto
import com.example.redmineagent.infrastructure.web.mapper.toStartedDto
import com.example.redmineagent.infrastructure.web.mapper.toStatusDto
import kotlinx.coroutines.runBlocking
import org.springframework.context.annotation.Lazy
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 同期 / Reconcile / 状態確認の REST API (詳細: docs/03-api-spec.md §2〜§5)。
 *
 * - POST `/api/sync` (`mode=incremental|full`)        : 差分同期を即時実行
 * - POST `/api/reconcile`                              : Reconcile を即時実行
 * - GET  `/api/sync/status`                            : 直近の同期状態
 * - GET  `/api/sync/runs?limit=N&kind=...`             : 同期履歴
 *
 * 走行中に POST が来た場合 ApplicationService が `SyncAlreadyRunningException` を投げ、
 * `SyncControllerExceptionHandler` で 409 + `SYNC_ALREADY_RUNNING` に変換される。
 *
 * ArchUnit ルール 3: infrastructure.web は domain.repository / domain.gateway を直接呼ばない。
 * そのため状態取得も `GetSyncStatusApplicationService` を経由する。
 *
 * Spring Boot 4.0 / WebMVC + MockMvc では `suspend fun` controller の async dispatch が
 * 安定しないので、`runBlocking` でブリッジして同期 controller として動かす。
 */
@RestController
@RequestMapping("/api")
class SyncController(
    @Lazy private val syncIssues: SyncIssuesApplicationService,
    @Lazy private val reconcile: ReconcileApplicationService,
    @Lazy private val getSyncStatus: GetSyncStatusApplicationService,
) {
    @PostMapping("/sync")
    fun startSync(
        @RequestParam(name = "mode", defaultValue = DEFAULT_SYNC_MODE) mode: String,
    ): SyncStartedDto {
        val syncMode = parseMode(mode)
        return runBlocking { syncIssues.execute(syncMode) }.toStartedDto()
    }

    @PostMapping("/reconcile")
    fun startReconcile(): SyncStartedDto = runBlocking { reconcile.execute() }.toStartedDto()

    @GetMapping("/sync/status")
    fun getStatus(): SyncStatusDto =
        runBlocking {
            val snapshot = getSyncStatus.getStatus()
            snapshot.state.toStatusDto(
                currentlyRunning = snapshot.currentlyRunning,
                currentRunId = snapshot.currentRunId,
            )
        }

    @GetMapping("/sync/runs")
    fun listRuns(
        @RequestParam(name = "limit", defaultValue = DEFAULT_LIMIT_STRING) limit: Int,
        @RequestParam(name = "kind", required = false) kind: String?,
    ): SyncRunsResponseDto =
        runBlocking {
            val capped = limit.coerceIn(1, MAX_LIMIT)
            val parsedKind = kind?.let(::parseRunKind)
            val items = getSyncStatus.listRecentRuns(capped, parsedKind).map { it.toRunDto() }
            SyncRunsResponseDto(items = items, total = items.size)
        }

    // -------------------------------------------------------------------------
    // private helpers
    // -------------------------------------------------------------------------

    private fun parseMode(raw: String): SyncMode =
        when (raw.lowercase()) {
            "incremental" -> SyncMode.INCREMENTAL
            "full" -> SyncMode.FULL
            else -> throw badRequest("mode", "must be 'incremental' or 'full': $raw")
        }

    private fun parseRunKind(raw: String): SyncRunKind =
        when (raw.lowercase()) {
            "incremental" -> SyncRunKind.INCREMENTAL
            "full" -> SyncRunKind.FULL
            "reconcile" -> SyncRunKind.RECONCILE
            else -> throw badRequest("kind", "must be 'incremental' / 'full' / 'reconcile': $raw")
        }

    private fun badRequest(
        field: String,
        reason: String,
    ): ResponseStatusException = ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request: $field: $reason")

    companion object {
        private const val DEFAULT_SYNC_MODE = "incremental"
        private const val DEFAULT_LIMIT_STRING = "20"
        private const val MAX_LIMIT = 100
    }
}
