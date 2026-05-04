import type { TicketHit } from "../types";

type SourcesPanelProps = {
  items: TicketHit[];
};

/**
 * 引用ソース (RAG ヒット) のサイドカードリスト。
 * URL クリックで Redmine の該当チケットを別タブで開く (F-05)。
 */
export function SourcesPanel({ items }: SourcesPanelProps) {
  if (items.length === 0) {
    return (
      <aside className="rounded-2xl border border-slate-200 bg-white/70 p-4 text-sm text-slate-500 shadow-sm">
        <h2 className="mb-2 flex items-center gap-2 text-base font-semibold text-slate-700">
          <span aria-hidden>📚</span>
          引用元
        </h2>
        <p className="text-xs leading-relaxed">
          関連チケットが見つかったらここに表示されます。
        </p>
      </aside>
    );
  }

  return (
    <aside className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm">
      <h2 className="mb-3 flex items-center gap-2 text-base font-semibold text-slate-700">
        <span aria-hidden>📚</span>
        引用元
        <span className="rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-700">
          {items.length}
        </span>
      </h2>
      <ul className="flex flex-col gap-3">
        {items.map((hit) => (
          <li key={hit.ticketId} className="group">
            <a
              href={hit.url}
              target="_blank"
              rel="noreferrer noopener"
              className="block rounded-xl border border-slate-100 p-3 text-sm transition hover:border-indigo-300 hover:bg-indigo-50/40 hover:shadow-sm"
            >
              <div className="flex items-center gap-2">
                <span className="rounded-md bg-slate-100 px-1.5 py-0.5 font-mono text-xs text-slate-600 group-hover:bg-indigo-100 group-hover:text-indigo-700">
                  #{hit.ticketId}
                </span>
                <TrackerBadge tracker={hit.tracker} />
                <StatusBadge status={hit.status} />
                <span className="ml-auto font-mono text-xs text-slate-400">
                  {hit.score.toFixed(2)}
                </span>
              </div>
              <p className="mt-1.5 font-medium text-slate-800 group-hover:text-indigo-700">
                {hit.subject}
              </p>
              <p className="mt-1 line-clamp-3 text-xs leading-relaxed text-slate-500">
                {hit.snippet}
              </p>
            </a>
          </li>
        ))}
      </ul>
    </aside>
  );
}

function TrackerBadge({ tracker }: { tracker: string }) {
  const cls = trackerColor(tracker);
  return (
    <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${cls}`}>
      {tracker}
    </span>
  );
}

function StatusBadge({ status }: { status: string }) {
  const cls = statusColor(status);
  return (
    <span className={`rounded px-1.5 py-0.5 text-xs ${cls}`}>{status}</span>
  );
}

function trackerColor(tracker: string): string {
  switch (tracker.toLowerCase()) {
    case "bug":
      return "bg-red-50 text-red-700";
    case "feature":
      return "bg-emerald-50 text-emerald-700";
    case "support":
    case "task":
      return "bg-sky-50 text-sky-700";
    default:
      return "bg-slate-50 text-slate-600";
  }
}

function statusColor(status: string): string {
  const s = status.toLowerCase();
  if (s.includes("close") || s.includes("resolve")) return "bg-slate-100 text-slate-500";
  if (s.includes("progress")) return "bg-amber-50 text-amber-700";
  return "bg-slate-50 text-slate-600";
}
