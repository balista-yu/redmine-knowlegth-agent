package com.example.redmineagent.domain.model

/**
 * 同期状態のスナップショット (`GET /api/sync/status` の元データ)。
 *
 * `SyncState` (永続化された静的状態) に加えて、走行中の sync_run があるかどうかを動的に
 * 合成した値オブジェクト。Domain Model なので Spring 等のフレームワーク非依存。
 */
data class SyncStatusSnapshot(
    val state: SyncState,
    val currentlyRunning: Boolean,
    val currentRunId: Long?,
) {
    init {
        if (currentRunId != null) {
            require(currentlyRunning) {
                "currentRunId is set but currentlyRunning is false: $currentRunId"
            }
        }
    }
}
