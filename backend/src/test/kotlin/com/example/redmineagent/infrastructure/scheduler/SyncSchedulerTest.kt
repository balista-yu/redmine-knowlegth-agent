package com.example.redmineagent.infrastructure.scheduler

import com.example.redmineagent.application.service.ReconcileApplicationService
import com.example.redmineagent.application.service.SyncIssuesApplicationService
import com.example.redmineagent.domain.model.SyncMode
import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Instant

/**
 * `SyncScheduler` の単体テスト。`@Scheduled` 経路自体は Spring 統合テストではなく、
 * メソッドを直接呼び出して各 ApplicationService への dispatch と例外時の挙動を検証する。
 */
class SyncSchedulerTest :
    FunSpec({
        val syncIssues = mockk<SyncIssuesApplicationService>()
        val reconcile = mockk<ReconcileApplicationService>()
        val scheduler = SyncScheduler(syncIssues, reconcile)

        fun dummySyncRun(kind: SyncRunKind): SyncRun =
            SyncRun(
                id = 1L,
                kind = kind,
                startedAt = Instant.parse("2026-01-01T00:00:00Z"),
                finishedAt = Instant.parse("2026-01-01T00:00:01Z"),
                ticketsFetched = 0,
                chunksUpserted = 0,
                chunksSkipped = 0,
                ticketsDeleted = 0,
                status = SyncRunStatus.SUCCESS,
                errorMessage = null,
            )

        test("scheduledIncrementalSync は SyncMode INCREMENTAL で execute を呼ぶ") {
            coEvery { syncIssues.execute(SyncMode.INCREMENTAL) } returns dummySyncRun(SyncRunKind.INCREMENTAL)

            scheduler.scheduledIncrementalSync()

            coVerify(exactly = 1) { syncIssues.execute(SyncMode.INCREMENTAL) }
        }

        test("scheduledReconcile は ReconcileApplicationService を呼ぶ") {
            coEvery { reconcile.execute() } returns dummySyncRun(SyncRunKind.RECONCILE)

            scheduler.scheduledReconcile()

            coVerify(exactly = 1) { reconcile.execute() }
        }

        test("ApplicationService の例外は握りつぶし scheduler は throw しない") {
            coEvery { syncIssues.execute(any()) } throws RuntimeException("Redmine 503")

            // 例外が伝播しないことを確認 (assertion 不要、throw すれば test fail)
            scheduler.scheduledIncrementalSync()

            coVerify(exactly = 1) { syncIssues.execute(SyncMode.INCREMENTAL) }
        }
    })
