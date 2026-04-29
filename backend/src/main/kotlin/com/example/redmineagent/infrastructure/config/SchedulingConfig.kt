package com.example.redmineagent.infrastructure.config

import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * `@Scheduled` を有効化する Spring 設定クラス。
 *
 * オニオンルール (`.claude/rules/architecture.md`) に従い `@EnableScheduling` は
 * `infrastructure/config/` のここに集約する。`@Scheduled` 自体は `infrastructure/scheduler/`
 * の `SyncScheduler` に置く (ArchUnit ルール 7)。
 */
@Configuration
@EnableScheduling
class SchedulingConfig
