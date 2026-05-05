package com.example.redmineagent.application.exception

/**
 * `SyncIssuesApplicationService.execute` / `ReconcileApplicationService.execute` 呼び出し時、
 * 既に同期が走行中だった場合に投げる。
 *
 * Infrastructure 層 (`SyncController`) は HTTP 409 + `SYNC_ALREADY_RUNNING` に変換する。
 * レスポンスに `currentRunId` を含めるため、走行中の
 * sync_run.id を保持できる場合は引数で渡す (取得不能な場合は `null`)。
 */
class SyncAlreadyRunningException(
    val currentRunId: Long? = null,
    message: String = "Sync is already running",
) : RuntimeException(message)
