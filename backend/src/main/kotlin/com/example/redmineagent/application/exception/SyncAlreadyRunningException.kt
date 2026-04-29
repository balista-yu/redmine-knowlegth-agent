package com.example.redmineagent.application.exception

/**
 * `SyncIssuesApplicationService.execute` 呼び出し時、既に同期が走行中だった場合に投げる。
 *
 * Infrastructure 層 (`SyncController`) は HTTP 409 + `SYNC_ALREADY_RUNNING` に変換する
 * (詳細: docs/03-api-spec.md §2)。重複検知は `AtomicBoolean` を `compareAndSet` で
 * アトミックに切り替えることで実現する。
 */
class SyncAlreadyRunningException(
    message: String = "Sync is already running",
) : RuntimeException(message)
