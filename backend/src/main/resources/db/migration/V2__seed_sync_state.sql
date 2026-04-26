-- =============================================================================
-- V2__seed_sync_state.sql: sync_state に 'redmine' source の初期行を投入
-- =============================================================================
-- 本フェーズでは source は 'redmine' 固定。Application 側は常に 1 行存在する
-- 前提で load() するため、起動時点でこの行を確保しておく。
--
-- ON CONFLICT は Flyway 再実行から守るためではなく (Flyway は migration を
-- 再実行しない)、env 移行や baseline 操作時の事故防止。
-- =============================================================================

INSERT INTO sync_state (source, tickets_total)
VALUES ('redmine', 0)
ON CONFLICT (source) DO NOTHING;
