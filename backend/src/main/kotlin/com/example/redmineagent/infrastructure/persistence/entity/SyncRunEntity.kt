package com.example.redmineagent.infrastructure.persistence.entity

import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncRunStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * `sync_run` テーブルの JPA Entity。
 *
 * `kind` / `status` は DB 側 CHECK 制約で値域が定義されているため、
 * 文字列として保存し Mapper で enum に変換する。
 */
@Entity
@Table(name = "sync_run")
class SyncRunEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long = 0,
    @Column(name = "kind", nullable = false, length = 32)
    var kind: String = "",
    @Column(name = "started_at", nullable = false)
    var startedAt: Instant = Instant.EPOCH,
    @Column(name = "finished_at")
    var finishedAt: Instant? = null,
    @Column(name = "tickets_fetched", nullable = false)
    var ticketsFetched: Int = 0,
    @Column(name = "chunks_upserted", nullable = false)
    var chunksUpserted: Int = 0,
    @Column(name = "chunks_skipped", nullable = false)
    var chunksSkipped: Int = 0,
    @Column(name = "tickets_deleted", nullable = false)
    var ticketsDeleted: Int = 0,
    @Column(name = "status", nullable = false, length = 16)
    var status: String = "",
    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,
) {
    fun toDomain(): SyncRun =
        SyncRun(
            id = id.takeIf { it > 0 },
            kind = SyncRunKind.valueOf(kind.uppercase()),
            startedAt = startedAt,
            finishedAt = finishedAt,
            ticketsFetched = ticketsFetched,
            chunksUpserted = chunksUpserted,
            chunksSkipped = chunksSkipped,
            ticketsDeleted = ticketsDeleted,
            status = SyncRunStatus.valueOf(status.uppercase()),
            errorMessage = errorMessage,
        )

    companion object {
        fun fromDomain(run: SyncRun): SyncRunEntity =
            SyncRunEntity(
                id = run.id ?: 0,
                kind = run.kind.name.lowercase(),
                startedAt = run.startedAt,
                finishedAt = run.finishedAt,
                ticketsFetched = run.ticketsFetched,
                chunksUpserted = run.chunksUpserted,
                chunksSkipped = run.chunksSkipped,
                ticketsDeleted = run.ticketsDeleted,
                status = run.status.name.lowercase(),
                errorMessage = run.errorMessage,
            )
    }
}
