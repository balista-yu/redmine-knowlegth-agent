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
      <aside className="rounded-md border border-slate-200 bg-white p-4 text-sm text-slate-500">
        <h2 className="mb-2 text-base font-semibold text-slate-700">引用元</h2>
        <p>関連チケットが見つかったらここに表示されます。</p>
      </aside>
    );
  }

  return (
    <aside className="rounded-md border border-slate-200 bg-white p-4">
      <h2 className="mb-3 text-base font-semibold text-slate-700">
        引用元 ({items.length})
      </h2>
      <ul className="flex flex-col gap-3">
        {items.map((hit) => (
          <li
            key={hit.ticketId}
            className="rounded-md border border-slate-100 p-3 text-sm"
          >
            <a
              href={hit.url}
              target="_blank"
              rel="noreferrer noopener"
              className="block text-slate-800 hover:underline"
            >
              <div className="flex items-center gap-2">
                <span className="rounded bg-slate-100 px-1.5 py-0.5 text-xs text-slate-600">
                  #{hit.ticketId}
                </span>
                <span className="text-xs text-slate-500">
                  {hit.tracker} / {hit.status}
                </span>
                <span className="ml-auto text-xs text-slate-400">
                  score {hit.score.toFixed(2)}
                </span>
              </div>
              <p className="mt-1 font-medium">{hit.subject}</p>
            </a>
            <p className="mt-2 line-clamp-3 text-xs text-slate-500">
              {hit.snippet}
            </p>
          </li>
        ))}
      </ul>
    </aside>
  );
}
