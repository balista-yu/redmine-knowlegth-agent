package com.example.redmineagent.infrastructure.persistence.jpa

import com.example.redmineagent.infrastructure.persistence.entity.SyncRunEntity
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * Spring Data JPA リポジトリ — `sync_run` 用。
 */
interface SyncRunJpaRepository : JpaRepository<SyncRunEntity, Long> {
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): List<SyncRunEntity>

    @Query("SELECT r FROM SyncRunEntity r WHERE r.kind = :kind ORDER BY r.startedAt DESC")
    fun findByKindOrderByStartedAtDesc(
        @Param("kind") kind: String,
        pageable: Pageable,
    ): List<SyncRunEntity>
}
