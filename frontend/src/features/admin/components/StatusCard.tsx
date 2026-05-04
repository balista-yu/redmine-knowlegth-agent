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
      className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm"
    >
      <header className="mb-4 flex items-center gap-2 border-b border-slate-100 pb-3">
        <span
          aria-hidden
          className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 text-base text-white shadow-sm"
        >
          ⚡
        </span>
        <h2 className="text-base font-semibold text-slate-800">同期状態</h2>
        {status.currentlyRunning ? (
          <span className="ml-auto inline-flex items-center gap-1.5 rounded-full bg-amber-100 px-3 py-0.5 text-xs font-medium text-amber-700">
            <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-amber-500" />
            実行中 {status.currentRunId !== null && `#${status.currentRunId}`}
          </span>
        ) : (
          <span className="ml-auto inline-flex items-center gap-1.5 rounded-full bg-emerald-100 px-3 py-0.5 text-xs font-medium text-emerald-700">
            <span className="h-1.5 w-1.5 rounded-full bg-emerald-500" />
            待機中
          </span>
        )}
      </header>
      <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
        <Row label="source" value={status.source} mono />
        <Row
          label="ticketsTotal"
          value={status.ticketsTotal.toLocaleString()}
          accent
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
  mono,
  accent,
}: {
  label: string;
  value: string;
  highlight?: boolean;
  mono?: boolean;
  accent?: boolean;
}) {
  return (
    <div className="flex items-baseline justify-between gap-3 sm:block">
      <dt className="font-mono text-xs uppercase tracking-wider text-slate-400">
        {label}
      </dt>
      <dd
        className={`mt-0.5 ${mono ? "font-mono" : ""} ${
          accent
            ? "text-base font-semibold text-indigo-700"
            : highlight
              ? "text-red-700"
              : "text-slate-800"
        }`}
      >
        {value}
      </dd>
    </div>
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
