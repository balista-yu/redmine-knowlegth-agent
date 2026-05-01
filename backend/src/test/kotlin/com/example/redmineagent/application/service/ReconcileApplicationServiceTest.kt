package com.example.redmineagent.application.service

import com.example.redmineagent.domain.gateway.RedmineGateway
import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import com.example.redmineagent.domain.repository.SyncStateRepository
import com.example.redmineagent.domain.repository.TicketChunkRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.time.Instant

/**
 * `ReconcileApplicationService` の単体テスト (docs/02-tasks.md T-1-11)。
 */
class ReconcileApplicationServiceTest :
    FunSpec({
        val redmineGateway = mockk<RedmineGateway>()
        val ticketChunkRepository = mockk<TicketChunkRepository>()
        val syncStateRepository = mockk<SyncStateRepository>()
        val service =
            ReconcileApplicationService(
                redmineGateway = redmineGateway,
                ticketChunkRepository = ticketChunkRepository,
                syncStateRepository = syncStateRepository,
            )

        fun stubInitialState() {
            coEvery { syncStateRepository.startRun(SyncRunKind.RECONCILE, any()) } answers {
                SyncRun(
                    id = 99L,
                    kind = SyncRunKind.RECONCILE,
                    startedAt = secondArg(),
                    finishedAt = null,
                    ticketsFetched = 0,
                    chunksUpserted = 0,
                    chunksSkipped = 0,
                    ticketsDeleted = 0,
                    status = SyncRunStatus.RUNNING,
                    errorMessage = null,
                )
            }
            coEvery { syncStateRepository.updateLastFullReconcileAt(any<Instant>()) } just Runs
            coEvery { syncStateRepository.updateTicketsTotal(any()) } just Runs
            coEvery { syncStateRepository.updateLastError(any()) } just Runs
        }

        test("Qdrant にしかない ticket は deleteByTicketId で削除される") {
            stubInitialState()
            coEvery { redmineGateway.listAllIssueIds() } returns setOf(1, 2, 3)
            coEvery { ticketChunkRepository.listAllTicketIds() } returns setOf(1, 2, 3, 4)
            coEvery { ticketChunkRepository.deleteByTicketId(any()) } just Runs
            val finishedSlot = slot<SyncRun>()
            coEvery { syncStateRepository.completeRun(capture(finishedSlot)) } answers { finishedSlot.captured }

            val result = service.execute()

            result.status shouldBe SyncRunStatus.SUCCESS
            result.kind shouldBe SyncRunKind.RECONCILE
            result.ticketsDeleted shouldBe 1
            result.ticketsFetched shouldBe 3
            coVerify(exactly = 1) { ticketChunkRepository.deleteByTicketId(4) }
            coVerify { syncStateRepository.updateLastFullReconcileAt(any()) }
            coVerify { syncStateRepository.updateTicketsTotal(3) }
        }

        test("Redmine と Qdrant が一致なら deleteByTicketId は呼ばれず ticketsDeleted は 0") {
            stubInitialState()
            coEvery { redmineGateway.listAllIssueIds() } returns setOf(10, 20, 30)
            coEvery { ticketChunkRepository.listAllTicketIds() } returns setOf(10, 20, 30)
            val finishedSlot = slot<SyncRun>()
            coEvery { syncStateRepository.completeRun(capture(finishedSlot)) } answers { finishedSlot.captured }

            val result = service.execute()

            result.ticketsDeleted shouldBe 0
            coVerify(exactly = 0) { ticketChunkRepository.deleteByTicketId(any()) }
        }

        test("Redmine 取得失敗なら failRun と last_error 更新で run を failed に記録し例外を伝播する") {
            stubInitialState()
            coEvery { redmineGateway.listAllIssueIds() } throws RuntimeException("Redmine 503 after retries")
            val failedSlot = slot<SyncRun>()
            coEvery { syncStateRepository.failRun(capture(failedSlot)) } answers { failedSlot.captured }

            shouldThrow<RuntimeException> { service.execute() }

            failedSlot.captured.status shouldBe SyncRunStatus.FAILED
            failedSlot.captured.errorMessage shouldBe "Redmine 503 after retries"
            coVerify { syncStateRepository.updateLastError("Redmine 503 after retries") }
            coVerify(exactly = 0) { syncStateRepository.completeRun(any()) }
            coVerify(exactly = 0) { syncStateRepository.updateLastFullReconcileAt(any()) }
            coVerify(exactly = 0) { ticketChunkRepository.deleteByTicketId(any()) }
        }
    })
