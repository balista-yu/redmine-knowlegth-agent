package com.example.redmineagent.infrastructure.scheduler

import com.example.redmineagent.application.service.ReconcileApplicationService
import com.example.redmineagent.application.service.SyncIssuesApplicationService
import com.example.redmineagent.domain.model.SyncMode
import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * `SyncScheduler` の単体テスト。`@Scheduled` 経路自体は Spring 統合テストではなく、
 * メソッドを直接呼び出して各 ApplicationService への dispatch と例外時の挙動を検証する。
 */
class SyncSchedulerTest {
    private val syncIssues = mockk<SyncIssuesApplicationService>()
    private val reconcile = mockk<ReconcileApplicationService>()
    private val scheduler = SyncScheduler(syncIssues, reconcile)

    @Test
    fun `scheduledIncrementalSync は SyncMode INCREMENTAL で execute を呼ぶ`() {
        coEvery { syncIssues.execute(SyncMode.INCREMENTAL) } returns dummySyncRun(SyncRunKind.INCREMENTAL)

        scheduler.scheduledIncrementalSync()

        coVerify(exactly = 1) { syncIssues.execute(SyncMode.INCREMENTAL) }
    }

    @Test
    fun `scheduledReconcile は ReconcileApplicationService を呼ぶ`() {
        coEvery { reconcile.execute() } returns dummySyncRun(SyncRunKind.RECONCILE)

        scheduler.scheduledReconcile()

        coVerify(exactly = 1) { reconcile.execute() }
    }

    @Test
    fun `ApplicationService の例外は握りつぶし scheduler は throw しない`() {
        coEvery { syncIssues.execute(any()) } throws RuntimeException("Redmine 503")

        // 例外が伝播しないことを確認 (assertion 不要、throw すれば test fail)
        scheduler.scheduledIncrementalSync()

        coVerify(exactly = 1) { syncIssues.execute(SyncMode.INCREMENTAL) }
    }

    private fun dummySyncRun(kind: SyncRunKind): SyncRun =
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
}
