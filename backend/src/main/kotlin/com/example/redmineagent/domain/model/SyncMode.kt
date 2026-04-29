package com.example.redmineagent.domain.model

/**
 * `SyncIssuesApplicationService.execute` の入力。
 *
 * - `INCREMENTAL`: `sync_state.last_sync_started_at` 以降に更新されたチケットのみ取得 (通常運用)
 * - `FULL`       : 時刻フィルタなしで全件再ロード (初回 / 復旧時)
 *
 * `SyncRunKind` のうち `RECONCILE` を除いたサブセット。Reconcile は別ユースケース
 * (`ReconcileApplicationService`) なのでここには含めない。
 */
enum class SyncMode {
    INCREMENTAL,
    FULL,
    ;

    fun toRunKind(): SyncRunKind =
        when (this) {
            INCREMENTAL -> SyncRunKind.INCREMENTAL
            FULL -> SyncRunKind.FULL
        }
}
