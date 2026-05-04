type SyncControlsProps = {
  disabled: boolean;
  onSync: () => void;
  onReconcile: () => void;
};

/**
 * 「今すぐ同期」「Reconcile」ボタンを並べたコントロール。走行中は両方 disabled。
 */
export function SyncControls({ disabled, onSync, onReconcile }: SyncControlsProps) {
  return (
    <div className="flex flex-wrap items-center gap-3">
      <button
        type="button"
        onClick={onSync}
        disabled={disabled}
        className="inline-flex items-center gap-2 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 px-4 py-2 text-sm font-medium text-white shadow-sm transition hover:from-indigo-600 hover:to-purple-700 hover:shadow-md disabled:from-slate-300 disabled:to-slate-300 disabled:shadow-none"
      >
        <span aria-hidden>⟳</span>
        今すぐ同期
      </button>
      <button
        type="button"
        onClick={onReconcile}
        disabled={disabled}
        className="inline-flex items-center gap-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-sm font-medium text-slate-700 shadow-sm transition hover:border-indigo-300 hover:bg-indigo-50 hover:text-indigo-700 disabled:border-slate-200 disabled:bg-slate-50 disabled:text-slate-400"
      >
        <span aria-hidden>🔍</span>
        Reconcile
      </button>
      {disabled && (
        <span className="text-xs text-slate-500">
          同期実行中 — 完了までお待ちください
        </span>
      )}
    </div>
  );
}
