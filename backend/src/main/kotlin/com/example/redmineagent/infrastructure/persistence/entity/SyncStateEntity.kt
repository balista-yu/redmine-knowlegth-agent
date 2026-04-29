package com.example.redmineagent.infrastructure.persistence.entity

import com.example.redmineagent.domain.model.SyncState
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * `sync_state` テーブルの JPA Entity。
 *
 * Domain `SyncState` とは別物。Mapper (`toDomain`) で変換する
 * (`.claude/rules/architecture.md` 「DTO の境界」)。
 */
@Entity
@Table(name = "sync_state")
class SyncStateEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Int = 0,
    @Column(name = "source", unique = true, nullable = false, length = 64)
    var source: String = "",
    @Column(name = "last_sync_started_at")
    var lastSyncStartedAt: Instant? = null,
    @Column(name = "last_sync_finished_at")
    var lastSyncFinishedAt: Instant? = null,
    @Column(name = "last_full_reconcile_at")
    var lastFullReconcileAt: Instant? = null,
    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,
    @Column(name = "tickets_total", nullable = false)
    var ticketsTotal: Int = 0,
) {
    fun toDomain(): SyncState =
        SyncState(
            source = source,
            lastSyncStartedAt = lastSyncStartedAt,
            lastSyncFinishedAt = lastSyncFinishedAt,
            lastFullReconcileAt = lastFullReconcileAt,
            lastError = lastError,
            ticketsTotal = ticketsTotal,
        )
}
