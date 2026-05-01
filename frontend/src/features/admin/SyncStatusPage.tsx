import { useCallback, useEffect, useState } from "react";
import {
  fetchSyncRuns,
  fetchSyncStatus,
  startReconcile,
  startSync,
} from "./api/syncApi";
import { RunsTable } from "./components/RunsTable";
import { StatusCard } from "./components/StatusCard";
import { SyncControls } from "./components/SyncControls";
import {
  SyncAlreadyRunningError,
  SyncApiHttpError,
  type SyncRun,
  type SyncStatus,
} from "./types";

const POLL_INTERVAL_MS = 5000;
const RUNS_LIMIT = 20;

/**
 * 同期状態ダッシュボード (T-3-3)。
 * - 5 秒間隔で `/api/sync/status` と `/api/sync/runs` をポーリング
 * - 「今すぐ同期」「Reconcile」ボタンで手動実行
 * - 走行中なら 409 + `SYNC_ALREADY_RUNNING` を赤バナー表示
 */
export function SyncStatusPage() {
  const [status, setStatus] = useState<SyncStatus | null>(null);
  const [runs, setRuns] = useState<SyncRun[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [actionInFlight, setActionInFlight] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const [s, r] = await Promise.all([
        fetchSyncStatus(),
        fetchSyncRuns(RUNS_LIMIT),
      ]);
      setStatus(s);
      setRuns(r.items);
    } catch (e) {
      setError(toMessage(e));
    }
  }, []);

  useEffect(() => {
    void refresh();
    const id = window.setInterval(() => {
      void refresh();
    }, POLL_INTERVAL_MS);
    return () => {
      window.clearInterval(id);
    };
  }, [refresh]);

  const handleSync = useCallback(async () => {
    setError(null);
    setActionInFlight(true);
    try {
      await startSync();
      await refresh();
    } catch (e) {
      setError(toMessage(e));
    } finally {
      setActionInFlight(false);
    }
  }, [refresh]);

  const handleReconcile = useCallback(async () => {
    setError(null);
    setActionInFlight(true);
    try {
      await startReconcile();
      await refresh();
    } catch (e) {
      setError(toMessage(e));
    } finally {
      setActionInFlight(false);
    }
  }, [refresh]);

  const controlsDisabled = actionInFlight || status?.currentlyRunning === true;

  return (
    <div className="min-h-screen bg-slate-50 px-6 py-6">
      <div className="mx-auto flex max-w-4xl flex-col gap-4">
        <h1 className="text-xl font-semibold text-slate-800">同期ダッシュボード</h1>
        {error !== null && (
          <div
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700"
          >
            {error}
          </div>
        )}
        {status === null ? (
          <p className="text-sm text-slate-500">読み込み中...</p>
        ) : (
          <StatusCard status={status} />
        )}
        <SyncControls
          disabled={controlsDisabled}
          onSync={() => void handleSync()}
          onReconcile={() => void handleReconcile()}
        />
        <h2 className="mt-4 text-base font-semibold text-slate-700">
          直近の実行履歴 (最大 {RUNS_LIMIT} 件)
        </h2>
        <RunsTable runs={runs} />
      </div>
    </div>
  );
}

function toMessage(err: unknown): string {
  if (err instanceof SyncAlreadyRunningError) {
    return `同期が既に実行中です${
      err.currentRunId !== null ? ` (run #${err.currentRunId})` : ""
    }`;
  }
  if (err instanceof SyncApiHttpError) {
    return `通信エラー: HTTP ${err.status} (${err.code})`;
  }
  if (err instanceof Error) return err.message;
  return "不明なエラーが発生しました";
}
