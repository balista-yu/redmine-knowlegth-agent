import {
  SyncAlreadyRunningError,
  SyncApiHttpError,
  type ApiError,
  type SyncRunsResponse,
  type SyncStartedResponse,
  type SyncStatus,
} from "../types";

/**
 * `/api/sync/*` エンドポイントの薄い fetch ラッパ。
 *
 * - 200 系: JSON デシリアライズして返却
 * - 409 + `SYNC_ALREADY_RUNNING`: `SyncAlreadyRunningError` に変換
 * - その他非 2xx: `SyncApiHttpError` に変換
 */

const HEADERS_JSON = { "Content-Type": "application/json" } as const;

export async function fetchSyncStatus(signal?: AbortSignal): Promise<SyncStatus> {
  const res = await fetch("/api/sync/status", { signal });
  if (!res.ok) throw await toError(res);
  return (await res.json()) as SyncStatus;
}

export async function fetchSyncRuns(
  limit = 20,
  signal?: AbortSignal,
): Promise<SyncRunsResponse> {
  const res = await fetch(`/api/sync/runs?limit=${limit}`, { signal });
  if (!res.ok) throw await toError(res);
  return (await res.json()) as SyncRunsResponse;
}

export async function startSync(
  mode: "incremental" | "full" = "incremental",
): Promise<SyncStartedResponse> {
  const res = await fetch(`/api/sync?mode=${mode}`, {
    method: "POST",
    headers: HEADERS_JSON,
  });
  if (!res.ok) throw await toError(res);
  return (await res.json()) as SyncStartedResponse;
}

export async function startReconcile(): Promise<SyncStartedResponse> {
  const res = await fetch("/api/reconcile", {
    method: "POST",
    headers: HEADERS_JSON,
  });
  if (!res.ok) throw await toError(res);
  return (await res.json()) as SyncStartedResponse;
}

async function toError(res: Response): Promise<Error> {
  const body = await safeReadJson(res);
  if (res.status === HTTP_CONFLICT && body?.code === "SYNC_ALREADY_RUNNING") {
    return new SyncAlreadyRunningError(body.currentRunId ?? null);
  }
  return new SyncApiHttpError(res.status, body?.code ?? "UNKNOWN");
}

async function safeReadJson(res: Response): Promise<ApiError | null> {
  try {
    return (await res.json()) as ApiError;
  } catch {
    return null;
  }
}

const HTTP_CONFLICT = 409;
