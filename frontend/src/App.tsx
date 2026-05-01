/**
 * アプリのトップ画面 (Phase 3 雛形)。T-3-2 で `ChatPage`、T-3-3 で
 * `SyncStatusPage` がここに組み込まれる予定。
 */
export function App() {
  return (
    <div className="min-h-screen bg-slate-50 text-slate-800">
      <header className="border-b bg-white px-6 py-4 shadow-sm">
        <h1 className="text-xl font-semibold">Redmine Knowledge Agent</h1>
      </header>
      <main className="mx-auto max-w-3xl px-6 py-8">
        <p className="text-base">
          Frontend skeleton ready. Chat UI (T-3-2) と Sync ダッシュボード
          (T-3-3) は後続のタスクで実装します。
        </p>
      </main>
    </div>
  );
}
