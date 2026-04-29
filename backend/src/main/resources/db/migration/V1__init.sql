-- =============================================================================
-- V1__init.sql: 同期メタデータ DB の初期スキーマ
-- =============================================================================
-- 詳細仕様: docs/01-design.md §4.2
--
-- テーブル:
--   sync_state  source ごとの直近同期状態 (1 source = 1 row)
--   sync_run    同期実行履歴
-- =============================================================================

CREATE TABLE sync_state (
    id                       SERIAL       PRIMARY KEY,
    source                   VARCHAR(64)  NOT NULL UNIQUE,
    last_sync_started_at     TIMESTAMPTZ,
    last_sync_finished_at    TIMESTAMPTZ,
    last_full_reconcile_at   TIMESTAMPTZ,
    last_error               TEXT,
    tickets_total            INT          NOT NULL DEFAULT 0
);

COMMENT ON TABLE  sync_state                       IS '同期 source ごとの直近状態 (1 source = 1 row)';
COMMENT ON COLUMN sync_state.source                IS '同期元の識別子 (本フェーズでは ''redmine'' 固定)';
COMMENT ON COLUMN sync_state.last_sync_started_at  IS '直近同期の開始時刻 (取り逃し防止のため終了時刻ではなく開始時刻を採用)';
COMMENT ON COLUMN sync_state.last_sync_finished_at IS '直近同期の完了時刻';
COMMENT ON COLUMN sync_state.last_full_reconcile_at IS '直近 Reconcile 完了時刻';
COMMENT ON COLUMN sync_state.last_error            IS '直近エラーメッセージ (成功時は NULL)';
COMMENT ON COLUMN sync_state.tickets_total         IS 'Qdrant 上のユニーク ticket_id 件数';


CREATE TABLE sync_run (
    -- BIGSERIAL: log テーブルなので 32-bit 上限を回避し SyncRunEntity (Long) と合わせる
    id                BIGSERIAL    PRIMARY KEY,
    kind              VARCHAR(32)  NOT NULL,
    started_at        TIMESTAMPTZ  NOT NULL,
    finished_at       TIMESTAMPTZ,
    tickets_fetched   INT          NOT NULL DEFAULT 0,
    chunks_upserted   INT          NOT NULL DEFAULT 0,
    chunks_skipped    INT          NOT NULL DEFAULT 0,
    tickets_deleted   INT          NOT NULL DEFAULT 0,
    status            VARCHAR(16)  NOT NULL,
    error_message     TEXT,
    CONSTRAINT chk_sync_run_kind   CHECK (kind   IN ('incremental', 'full', 'reconcile')),
    CONSTRAINT chk_sync_run_status CHECK (status IN ('running', 'success', 'failed'))
);

-- 直近 N 件取得 + kind 絞り込み (GET /api/sync/runs) の対応インデックス
CREATE INDEX idx_sync_run_started_at      ON sync_run (started_at DESC);
CREATE INDEX idx_sync_run_kind_started_at ON sync_run (kind, started_at DESC);

COMMENT ON TABLE  sync_run                  IS '同期実行履歴';
COMMENT ON COLUMN sync_run.kind             IS 'incremental / full / reconcile';
COMMENT ON COLUMN sync_run.status           IS 'running / success / failed';
COMMENT ON COLUMN sync_run.tickets_fetched  IS 'Redmine から取得したチケット件数';
COMMENT ON COLUMN sync_run.chunks_upserted  IS 'Qdrant に upsert したチャンク件数';
COMMENT ON COLUMN sync_run.chunks_skipped   IS 'hash 一致でスキップしたチャンク件数';
COMMENT ON COLUMN sync_run.tickets_deleted  IS 'Reconcile で削除した ticket 件数';
