import type { SyncRun } from "../types";

type RunsTableProps = {
  runs: SyncRun[];
};

/**
 * `GET /api/sync/runs?limit=N` の履歴を表で表示する (新しい順)。
 */
export function RunsTable({ runs }: RunsTableProps) {
  if (runs.length === 0) {
    return (
      <p className="rounded-md border border-dashed border-slate-300 bg-white p-6 text-center text-sm text-slate-500">
        実行履歴はまだありません。
      </p>
    );
  }
  return (
    <div className="overflow-x-auto rounded-md border border-slate-200 bg-white shadow-sm">
      <table className="min-w-full divide-y divide-slate-200 text-sm">
        <thead className="bg-slate-50 text-xs uppercase text-slate-500">
          <tr>
            <Th>id</Th>
            <Th>kind</Th>
            <Th>started</Th>
            <Th>finished</Th>
            <Th>status</Th>
            <Th>fetched</Th>
            <Th>upserted</Th>
            <Th>skipped</Th>
            <Th>deleted</Th>
            <Th>error</Th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {runs.map((r) => (
            <tr key={r.id}>
              <Td>{r.id}</Td>
              <Td>{r.kind}</Td>
              <Td>{formatDateTime(r.startedAt)}</Td>
              <Td>{formatDateTime(r.finishedAt)}</Td>
              <Td>
                <StatusBadge status={r.status} />
              </Td>
              <Td>{r.ticketsFetched}</Td>
              <Td>{r.chunksUpserted}</Td>
              <Td>{r.chunksSkipped}</Td>
              <Td>{r.ticketsDeleted}</Td>
              <Td className="max-w-[24ch] truncate text-red-700" title={r.errorMessage ?? ""}>
                {r.errorMessage ?? "-"}
              </Td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function Th({ children }: { children: React.ReactNode }) {
  return <th className="px-3 py-2 text-left font-medium">{children}</th>;
}

function Td({
  children,
  className,
  title,
}: {
  children: React.ReactNode;
  className?: string;
  title?: string;
}) {
  return (
    <td
      className={`px-3 py-2 text-slate-700 ${className ?? ""}`}
      title={title}
    >
      {children}
    </td>
  );
}

function StatusBadge({ status }: { status: SyncRun["status"] }) {
  const cls: Record<SyncRun["status"], string> = {
    running: "bg-amber-100 text-amber-700",
    success: "bg-emerald-100 text-emerald-700",
    failed: "bg-red-100 text-red-700",
  };
  return (
    <span
      className={`rounded px-1.5 py-0.5 text-xs font-medium ${cls[status]}`}
    >
      {status}
    </span>
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
