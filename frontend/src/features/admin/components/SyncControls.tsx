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
    <div className="flex gap-3">
      <button
        type="button"
        onClick={onSync}
        disabled={disabled}
        className="rounded-md bg-slate-800 px-4 py-2 text-sm font-medium text-white hover:bg-slate-700 disabled:bg-slate-300"
      >
        今すぐ同期
      </button>
      <button
        type="button"
        onClick={onReconcile}
        disabled={disabled}
        className="rounded-md bg-white px-4 py-2 text-sm font-medium text-slate-800 ring-1 ring-slate-300 hover:bg-slate-100 disabled:opacity-50"
      >
        Reconcile
      </button>
    </div>
  );
}
