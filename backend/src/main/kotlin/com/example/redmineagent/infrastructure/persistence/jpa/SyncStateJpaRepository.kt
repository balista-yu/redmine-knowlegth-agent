package com.example.redmineagent.infrastructure.persistence.jpa

import com.example.redmineagent.infrastructure.persistence.entity.SyncStateEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Spring Data JPA リポジトリ — `sync_state` 用。
 *
 * Domain interface `SyncStateRepository` の実装の内側で利用する。
 * 直接 Application 層から呼んではいけない (ArchUnit ルール 2 / 3)。
 */
interface SyncStateJpaRepository : JpaRepository<SyncStateEntity, Int> {
    fun findBySource(source: String): SyncStateEntity?
}
