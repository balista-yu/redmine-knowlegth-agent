import type { SyncStatus } from "../types";

type StatusCardProps = {
  status: SyncStatus;
};

/**
 * `GET /api/sync/status` の現在状態を 1 枚のカードに整形して表示する。
 */
export function StatusCard({ status }: StatusCardProps) {
  return (
    <section
      aria-label="同期状態"
      className="rounded-md border border-slate-200 bg-white p-4 shadow-sm"
    >
      <header className="mb-3 flex items-center gap-2">
        <h2 className="text-base font-semibold text-slate-700">同期状態</h2>
        {status.currentlyRunning ? (
          <span className="rounded bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700">
            実行中 {status.currentRunId !== null && `#${status.currentRunId}`}
          </span>
        ) : (
          <span className="rounded bg-emerald-100 px-2 py-0.5 text-xs font-medium text-emerald-700">
            待機中
          </span>
        )}
      </header>
      <dl className="grid grid-cols-[140px_1fr] gap-y-1 text-sm">
        <Row label="source" value={status.source} />
        <Row
          label="ticketsTotal"
          value={status.ticketsTotal.toLocaleString()}
        />
        <Row
          label="lastSyncStartedAt"
          value={formatDateTime(status.lastSyncStartedAt)}
        />
        <Row
          label="lastSyncFinishedAt"
          value={formatDateTime(status.lastSyncFinishedAt)}
        />
        <Row
          label="lastFullReconcileAt"
          value={formatDateTime(status.lastFullReconcileAt)}
        />
        <Row
          label="lastError"
          value={status.lastError ?? "-"}
          highlight={status.lastError !== null}
        />
      </dl>
    </section>
  );
}

function Row({
  label,
  value,
  highlight,
}: {
  label: string;
  value: string;
  highlight?: boolean;
}) {
  return (
    <>
      <dt className="text-slate-500">{label}</dt>
      <dd className={highlight ? "text-red-700" : "text-slate-800"}>{value}</dd>
    </>
  );
}

function formatDateTime(iso: string | null): string {
  if (iso === null) return "-";
  try {
    return new Date(iso).toLocaleString();
  } catch {
    return iso;
  }
}
