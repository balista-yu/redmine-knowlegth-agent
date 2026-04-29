package com.example.redmineagent.infrastructure.scheduler

import com.example.redmineagent.application.service.ReconcileApplicationService
import com.example.redmineagent.application.service.SyncIssuesApplicationService
import com.example.redmineagent.domain.model.SyncMode
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * cron で `SyncIssuesApplicationService` / `ReconcileApplicationService` を起動するスケジューラ。
 *
 * - cron 値は `application.yml` 経由で `app.sync.cron` / `app.sync.reconcile-cron` に外出し
 * - `@Scheduled` メソッドは非 suspend なので `runBlocking` で suspend execute を呼び出す
 *   (Spring 4.0 でも `@Async` + `CoroutineScope` を併用しない限り `@Scheduled` は同期実行の前提)
 * - 例外を握りつぶさず WARN ログを残し、ApplicationService 内部で `failRun` 済み状態を期待する
 *   (次回実行は cron で改めて起動する。スケジューラ自身では retry しない方針)
 */
@Component
class SyncScheduler(
    // @Lazy: 起動時に ApplicationService → Qdrant/Ollama の eager 初期化チェーンを発火させない
    // (HealthCheckTest など DB / Qdrant 不在の環境でも起動できるようにする)
    @Lazy private val syncIssues: SyncIssuesApplicationService,
    @Lazy private val reconcile: ReconcileApplicationService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(cron = "\${app.sync.cron}")
    fun scheduledIncrementalSync() {
        runScheduledJob(JOB_INCREMENTAL_SYNC) {
            runBlocking { syncIssues.execute(SyncMode.INCREMENTAL) }
        }
    }

    @Scheduled(cron = "\${app.sync.reconcile-cron}")
    fun scheduledReconcile() {
        runScheduledJob(JOB_RECONCILE) {
            runBlocking { reconcile.execute() }
        }
    }

    @Suppress("TooGenericExceptionCaught") // スケジューラは何が起きても次回実行に影響を与えない方針
    private fun runScheduledJob(
        jobName: String,
        block: () -> Unit,
    ) {
        logger.info("Scheduled job triggered: {}", jobName)
        try {
            block()
            logger.info("Scheduled job finished: {}", jobName)
        } catch (e: RuntimeException) {
            // ApplicationService 側で sync_run の failed 記録は既に済んでいる前提
            // ここでは次回実行に影響を与えないため握りつぶす (= 再 throw しない) が、必ず WARN
            logger.warn("Scheduled job failed (will retry on next cron): job={}, error={}", jobName, e.message, e)
        }
    }

    companion object {
        private const val JOB_INCREMENTAL_SYNC = "incremental-sync"
        private const val JOB_RECONCILE = "reconcile"
    }
}
