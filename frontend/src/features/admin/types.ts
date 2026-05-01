/**
 * `/api/sync/*` エンドポイントの DTO 型 (docs/03-api-spec.md §2〜§5)。
 */

export type SyncStatus = {
  source: string;
  lastSyncStartedAt: string | null;
  lastSyncFinishedAt: string | null;
  lastFullReconcileAt: string | null;
  lastError: string | null;
  ticketsTotal: number;
  currentlyRunning: boolean;
  currentRunId: number | null;
};

export type SyncRunKind = "incremental" | "full" | "reconcile";
export type SyncRunStatusValue = "running" | "success" | "failed";

export type SyncRun = {
  id: number;
  kind: SyncRunKind;
  startedAt: string;
  finishedAt: string | null;
  status: SyncRunStatusValue;
  ticketsFetched: number;
  chunksUpserted: number;
  chunksSkipped: number;
  ticketsDeleted: number;
  errorMessage: string | null;
};

export type SyncRunsResponse = {
  items: SyncRun[];
  total: number;
};

export type SyncStartedResponse = {
  runId: number;
  kind: SyncRunKind;
  startedAt: string;
  status: SyncRunStatusValue;
};

/** 409 Conflict など API 共通エラーレスポンス (docs/03-api-spec.md §6)。 */
export type ApiError = {
  code: string;
  message: string;
  currentRunId?: number;
};

/** 走行中で 409 が返ったときに throw する例外。 */
export class SyncAlreadyRunningError extends Error {
  constructor(public readonly currentRunId: number | null) {
    super("Sync is already running");
    this.name = "SyncAlreadyRunningError";
  }
}

/** その他の HTTP エラー。 */
export class SyncApiHttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
  ) {
    super(`Sync API HTTP ${status} (${code})`);
    this.name = "SyncApiHttpError";
  }
}
