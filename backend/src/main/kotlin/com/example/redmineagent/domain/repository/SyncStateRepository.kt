package com.example.redmineagent.domain.repository

import com.example.redmineagent.domain.model.SyncRun
import com.example.redmineagent.domain.model.SyncRunKind
import com.example.redmineagent.domain.model.SyncState
import java.time.Instant

/**
 * 同期メタデータ (sync_state / sync_run) の永続化抽象。
 *
 * Infrastructure 層では Spring Data JPA + PostgreSQL で実装される (`JpaSyncStateRepository`)。
 *
 * 詳細仕様: docs/01-design.md §4.2, §5.1, §5.3
 */
interface SyncStateRepository {
    // -------------------------------------------------------------------------
    // sync_state (1 source = 1 row)
    // -------------------------------------------------------------------------

    /** 'redmine' source の現在状態を取得する (起動後は必ず 1 行存在する前提、無ければ例外)。 */
    suspend fun load(): SyncState

    /** 同期開始時刻を更新する (取り逃し防止のため終了時刻ではなく開始時刻を保存)。 */
    suspend fun updateLastSyncStartedAt(at: Instant)

    /** Reconcile 完了時刻を更新する。 */
    suspend fun updateLastFullReconcileAt(at: Instant)

    /** Qdrant 上のユニークチケット件数を更新する。 */
    suspend fun updateTicketsTotal(total: Int)

    /** 直近エラー (成功時は null) を更新する。 */
    suspend fun updateLastError(message: String?)

    // -------------------------------------------------------------------------
    // sync_run (実行履歴、N 行)
    // -------------------------------------------------------------------------

    /**
     * 新しい実行を `running` で開始する。返り値は DB 採番された id を含む `SyncRun`。
     */
    suspend fun startRun(
        kind: SyncRunKind,
        startedAt: Instant,
    ): SyncRun

    /**
     * 既存実行を完了状態 (`success`) で更新する。
     * 引数の `run` は `id` 必須、`status=SUCCESS`、`finishedAt` 設定済み、各 count 設定済みである前提。
     */
    suspend fun completeRun(run: SyncRun): SyncRun

    /**
     * 既存実行を失敗状態 (`failed`) で更新する。
     * `run.errorMessage` を必ず設定して渡す。
     */
    suspend fun failRun(run: SyncRun): SyncRun

    /**
     * 直近の実行履歴を新しい順で取得する。
     *
     * @param kind null なら全種別、指定すれば該当 kind に絞る
     */
    suspend fun listRecentRuns(
        limit: Int,
        kind: SyncRunKind? = null,
    ): List<SyncRun>
}
