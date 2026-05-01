package com.example.redmineagent.infrastructure.persistence

import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant

/**
 * Testcontainers (postgres:16) で Flyway 自動実行 + JpaSyncStateRepository の動作検証。
 *
 * T-1-9 テスト要件:
 *  - Flyway 自動実行確認 (load() で seed 済み redmine 行が取れる)
 *  - load → updateLastSyncStartedAt → load で値反映
 *  - startRun → completeRun → listRecentRuns で履歴取得
 *  - failRun → status=failed / errorMessage 反映
 */
@SpringBootTest(
    classes = [JpaSyncStateRepositoryIT.TestApp::class],
    properties = [
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
    ],
)
@Testcontainers
class JpaSyncStateRepositoryIT {
    @SpringBootApplication
    class TestApp

    @Autowired
    private lateinit var repo: JpaSyncStateRepository

    @Test
    fun `load で V2 seed 済みの redmine 行が取得できる`() =
        runTest {
            val state = repo.load()

            state.source shouldBe "redmine"
            state.ticketsTotal shouldBe 0
        }

    @Test
    fun `updateLastSyncStartedAt で値が反映される`() =
        runTest {
            val now = Instant.parse("2026-04-29T10:00:00Z")

            repo.updateLastSyncStartedAt(now)
            val state = repo.load()

            state.lastSyncStartedAt shouldBe now
        }

    @Test
    fun `startRun → completeRun → listRecentRuns で履歴が取得できる`() =
        runTest {
            val started = Instant.parse("2026-04-29T11:00:00Z")
            val finished = Instant.parse("2026-04-29T11:05:00Z")

            val run = repo.startRun(SyncRunKind.INCREMENTAL, started)
            run.id.shouldNotBeNull()
            run.status shouldBe SyncRunStatus.RUNNING

            val completed =
                run.copy(
                    finishedAt = finished,
                    ticketsFetched = 10,
                    chunksUpserted = 30,
                    chunksSkipped = 5,
                    ticketsDeleted = 0,
                    status = SyncRunStatus.SUCCESS,
                )
            repo.completeRun(completed)

            val recent = repo.listRecentRuns(limit = 5)
            recent.shouldHaveSize(1)
            recent[0].status shouldBe SyncRunStatus.SUCCESS
            recent[0].chunksUpserted shouldBe 30
        }

    @Test
    fun `failRun で status=failed と errorMessage が反映される`() =
        runTest {
            val started = Instant.parse("2026-04-29T12:00:00Z")
            val run = repo.startRun(SyncRunKind.RECONCILE, started)

            val failed =
                run.copy(
                    finishedAt = Instant.parse("2026-04-29T12:00:30Z"),
                    status = SyncRunStatus.FAILED,
                    errorMessage = "boom",
                )
            repo.failRun(failed)

            val recent = repo.listRecentRuns(limit = 1, kind = SyncRunKind.RECONCILE)
            recent[0].status shouldBe SyncRunStatus.FAILED
            recent[0].errorMessage shouldBe "boom"
        }

    companion object {
        // Testcontainers 2.x の PostgreSQLContainer は型パラメータ無し (旧 SELF パターン廃止)
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:16")
                .withDatabaseName("redmineagent")
                .withUsername("redmineagent")
                .withPassword("redmineagent")

        @JvmStatic
        @DynamicPropertySource
        fun dataSourceProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }
}
