package com.example.redmineagent.infrastructure.web

import com.example.redmineagent.application.service.GetSyncStatusApplicationService
import com.example.redmineagent.application.service.ReconcileApplicationService
import com.example.redmineagent.application.service.SyncIssuesApplicationService
import com.example.redmineagent.domain.model.SyncMode
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.infrastructure.web.dto.ApiErrorDto
import com.example.redmineagent.infrastructure.web.dto.SyncRunsResponseDto
import com.example.redmineagent.infrastructure.web.dto.SyncStartedDto
import com.example.redmineagent.infrastructure.web.dto.SyncStatusDto
import com.example.redmineagent.infrastructure.web.mapper.toRunDto
import com.example.redmineagent.infrastructure.web.mapper.toStartedDto
import com.example.redmineagent.infrastructure.web.mapper.toStatusDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
 * 同期 / Reconcile / 状態確認の REST API。
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
@Tag(name = "sync", description = "Redmine → Qdrant 同期 / 状態確認")
class SyncController(
    @Lazy private val syncIssues: SyncIssuesApplicationService,
    @Lazy private val reconcile: ReconcileApplicationService,
    @Lazy private val getSyncStatus: GetSyncStatusApplicationService,
) {
    @PostMapping("/sync")
    @Operation(
        summary = "差分同期を即時実行",
        description = "Redmine から差分 (incremental) または全件 (full) を取得し Qdrant に upsert する。",
    )
    @ApiResponse(responseCode = "200", description = "同期 run の開始情報")
    @ApiResponse(
        responseCode = "409",
        description = "別の同期が実行中",
        content = [Content(schema = Schema(implementation = ApiErrorDto::class))],
    )
    @ApiResponse(
        responseCode = "400",
        description = "mode が 'incremental' / 'full' 以外",
        content = [Content(schema = Schema(implementation = ApiErrorDto::class))],
    )
    fun startSync(
        @Parameter(description = "同期モード", schema = Schema(allowableValues = ["incremental", "full"]))
        @RequestParam(name = "mode", defaultValue = DEFAULT_SYNC_MODE) mode: String,
    ): SyncStartedDto {
        val syncMode = parseMode(mode)
        return runBlocking { syncIssues.execute(syncMode) }.toStartedDto()
    }

    @PostMapping("/reconcile")
    @Operation(
        summary = "Reconcile を即時実行",
        description = "Redmine の現存 issue 一覧と Qdrant の保存内容を突き合わせ、削除済みの孤児 chunk を除去する。",
    )
    @ApiResponse(responseCode = "200", description = "Reconcile run の開始情報")
    @ApiResponse(
        responseCode = "409",
        description = "別の同期が実行中",
        content = [Content(schema = Schema(implementation = ApiErrorDto::class))],
    )
    fun startReconcile(): SyncStartedDto = runBlocking { reconcile.execute() }.toStartedDto()

    @GetMapping("/sync/status")
    @Operation(
        summary = "直近の同期状態を取得",
        description = "最終同期時刻 / 最終 Reconcile 時刻 / 走行中フラグ等を返す。",
    )
    fun getStatus(): SyncStatusDto =
        runBlocking {
            val snapshot = getSyncStatus.getStatus()
            snapshot.state.toStatusDto(
                currentlyRunning = snapshot.currentlyRunning,
                currentRunId = snapshot.currentRunId,
            )
        }

    @GetMapping("/sync/runs")
    @Operation(
        summary = "同期履歴を取得",
        description = "直近の sync_run を新しい順で返す。kind 指定で incremental / full / reconcile を絞り込み可能。",
    )
    fun listRuns(
        @Parameter(description = "取得件数 (1..$MAX_LIMIT)")
        @RequestParam(name = "limit", defaultValue = DEFAULT_LIMIT_STRING) limit: Int,
        @Parameter(description = "種別フィルタ", schema = Schema(allowableValues = ["incremental", "full", "reconcile"]))
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
