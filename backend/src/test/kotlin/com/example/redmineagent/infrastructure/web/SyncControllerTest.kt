package com.example.redmineagent.infrastructure.web

import com.example.redmineagent.application.exception.SyncAlreadyRunningException
import com.example.redmineagent.application.service.GetSyncStatusApplicationService
import com.example.redmineagent.application.service.ReconcileApplicationService
import com.example.redmineagent.application.service.SyncIssuesApplicationService
import com.example.redmineagent.domain.model.SyncMode
import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import com.example.redmineagent.domain.model.SyncState
import com.example.redmineagent.domain.model.SyncStatusSnapshot
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.time.Instant

@WebMvcTest(controllers = [SyncController::class])
@Import(SyncControllerExceptionHandler::class, SyncControllerTest.MockBeans::class)
class SyncControllerTest
    @Autowired
    constructor(
        private val mvc: MockMvc,
        private val syncIssues: SyncIssuesApplicationService,
        private val reconcile: ReconcileApplicationService,
        private val getSyncStatus: GetSyncStatusApplicationService,
    ) {
        @BeforeEach
        fun resetMocks() {
            clearMocks(syncIssues, reconcile, getSyncStatus)
        }

        @Test
        fun `POST api sync 正常系で 200 と SyncStartedDto を返す`() {
            coEvery { syncIssues.execute(SyncMode.INCREMENTAL) } returns
                completedRun(id = 42L, kind = SyncRunKind.INCREMENTAL)

            mvc
                .post("/api/sync") {
                    contentType = MediaType.APPLICATION_JSON
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.runId") { value(42) }
                    jsonPath("$.kind") { value("incremental") }
                    jsonPath("$.status") { value("success") }
                }
        }

        @Test
        fun `POST api sync 走行中なら 409 と SYNC_ALREADY_RUNNING`() {
            coEvery { syncIssues.execute(any()) } throws
                SyncAlreadyRunningException(currentRunId = 41L)

            mvc
                .post("/api/sync") {
                    contentType = MediaType.APPLICATION_JSON
                }.andExpect {
                    status { isConflict() }
                    jsonPath("$.code") { value("SYNC_ALREADY_RUNNING") }
                    jsonPath("$.currentRunId") { value(41) }
                }
        }

        @Test
        fun `POST api sync mode=full は SyncMode FULL で execute される`() {
            coEvery { syncIssues.execute(SyncMode.FULL) } returns
                completedRun(id = 50L, kind = SyncRunKind.FULL)

            mvc
                .post("/api/sync?mode=full") {
                    contentType = MediaType.APPLICATION_JSON
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.kind") { value("full") }
                }
            coVerify(exactly = 1) { syncIssues.execute(SyncMode.FULL) }
        }

        @Test
        fun `GET api sync status は仕様の JSON 構造を返す`() {
            coEvery { getSyncStatus.getStatus() } returns
                SyncStatusSnapshot(
                    state =
                        SyncState(
                            source = "redmine",
                            lastSyncStartedAt = Instant.parse("2026-04-26T10:00:00Z"),
                            lastSyncFinishedAt = Instant.parse("2026-04-26T10:00:42Z"),
                            lastFullReconcileAt = Instant.parse("2026-04-21T03:00:15Z"),
                            lastError = null,
                            ticketsTotal = 1247,
                        ),
                    currentlyRunning = false,
                    currentRunId = null,
                )

            mvc.get("/api/sync/status").andExpect {
                status { isOk() }
                jsonPath("$.source") { value("redmine") }
                jsonPath("$.lastSyncStartedAt") { value("2026-04-26T10:00:00Z") }
                jsonPath("$.lastSyncFinishedAt") { value("2026-04-26T10:00:42Z") }
                jsonPath("$.lastFullReconcileAt") { value("2026-04-21T03:00:15Z") }
                jsonPath("$.lastError") { isEmpty() }
                jsonPath("$.ticketsTotal") { value(1247) }
                jsonPath("$.currentlyRunning") { value(false) }
                jsonPath("$.currentRunId") { isEmpty() }
            }
        }

        @Test
        fun `GET api sync status は走行中なら currentlyRunning と currentRunId を返す`() {
            coEvery { getSyncStatus.getStatus() } returns
                SyncStatusSnapshot(
                    state = dummyState(),
                    currentlyRunning = true,
                    currentRunId = 99L,
                )

            mvc.get("/api/sync/status").andExpect {
                status { isOk() }
                jsonPath("$.currentlyRunning") { value(true) }
                jsonPath("$.currentRunId") { value(99) }
            }
        }

        @Test
        fun `GET api sync runs は limit を kind フィルタとともに反映する`() {
            coEvery {
                getSyncStatus.listRecentRuns(5, SyncRunKind.RECONCILE)
            } returns listOf(completedRun(id = 7L, kind = SyncRunKind.RECONCILE))

            mvc.get("/api/sync/runs?limit=5&kind=reconcile").andExpect {
                status { isOk() }
                jsonPath("$.items.length()") { value(1) }
                jsonPath("$.items[0].id") { value(7) }
                jsonPath("$.items[0].kind") { value("reconcile") }
                jsonPath("$.total") { value(1) }
            }
        }

        @Test
        fun `GET api sync runs は limit 上限 100 でクランプされる`() {
            coEvery { getSyncStatus.listRecentRuns(100, kind = null) } returns emptyList()

            mvc.get("/api/sync/runs?limit=999").andExpect {
                status { isOk() }
            }
            coVerify(exactly = 1) { getSyncStatus.listRecentRuns(100, kind = null) }
        }

        // ---------------------------------------------------------------------
        // helpers / test config
        // ---------------------------------------------------------------------

        private fun completedRun(
            id: Long,
            kind: SyncRunKind,
        ): SyncRun =
            SyncRun(
                id = id,
                kind = kind,
                startedAt = Instant.parse("2026-04-29T00:00:00Z"),
                finishedAt = Instant.parse("2026-04-29T00:00:30Z"),
                ticketsFetched = 3,
                chunksUpserted = 5,
                chunksSkipped = 2,
                ticketsDeleted = 0,
                status = SyncRunStatus.SUCCESS,
                errorMessage = null,
            )

        private fun dummyState(): SyncState =
            SyncState(
                source = "redmine",
                lastSyncStartedAt = null,
                lastSyncFinishedAt = null,
                lastFullReconcileAt = null,
                lastError = null,
                ticketsTotal = 0,
            )

        @TestConfiguration
        class MockBeans {
            @Bean
            fun syncIssuesApplicationService(): SyncIssuesApplicationService = mockk(relaxed = true)

            @Bean
            fun reconcileApplicationService(): ReconcileApplicationService = mockk(relaxed = true)

            @Bean
            fun getSyncStatusApplicationService(): GetSyncStatusApplicationService = mockk(relaxed = true)
        }
    }
