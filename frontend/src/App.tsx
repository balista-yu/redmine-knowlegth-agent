import { useEffect, useState } from "react";
import { ChatPage } from "./features/chat/ChatPage";
import { SyncStatusPage } from "./features/admin/SyncStatusPage";

type Route = "chat" | "admin";

/**
 * 最小ルーティング (location.hash で chat / admin を切り替え)。
 * react-router を入れるほどではないので hash routing で簡易実装。
 */
export function App() {
  const [route, setRoute] = useState<Route>(() => readRoute());

  useEffect(() => {
    const onHashChange = () => setRoute(readRoute());
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 via-indigo-50/30 to-purple-50/30 text-slate-800">
      <nav className="sticky top-0 z-10 border-b border-slate-200/70 bg-white/80 shadow-sm backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-4 px-6 py-3">
          <a href="#/chat" className="flex items-center gap-2">
            <span
              aria-hidden
              className="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 text-sm font-bold text-white shadow-sm"
            >
              R
            </span>
            <strong className="text-base font-semibold tracking-tight">
              Redmine Knowledge Agent
            </strong>
          </a>
          <div className="ml-auto flex items-center gap-1">
            <NavLink href="#/chat" current={route === "chat"}>
              💬 Chat
            </NavLink>
            <NavLink href="#/admin" current={route === "admin"}>
              ⚙ Sync
            </NavLink>
          </div>
        </div>
      </nav>
      <div className="mx-auto max-w-6xl">
        {route === "chat" ? <ChatPage /> : <SyncStatusPage />}
      </div>
    </div>
  );
}

function readRoute(): Route {
  return window.location.hash === "#/admin" ? "admin" : "chat";
}

function NavLink({
  href,
  current,
  children,
}: {
  href: string;
  current: boolean;
  children: React.ReactNode;
}) {
  return (
    <a
      href={href}
      className={
        current
          ? "rounded-lg bg-gradient-to-br from-indigo-500/10 to-purple-500/10 px-3 py-1.5 text-sm font-medium text-indigo-700 ring-1 ring-indigo-200"
          : "rounded-lg px-3 py-1.5 text-sm text-slate-600 transition hover:bg-slate-100 hover:text-slate-900"
      }
    >
      {children}
    </a>
  );
}
