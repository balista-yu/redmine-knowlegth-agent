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
    <div className="min-h-screen bg-slate-50 text-slate-800">
      <nav className="border-b bg-white px-6 py-3 shadow-sm">
        <div className="mx-auto flex max-w-4xl items-center gap-4">
          <strong className="text-base font-semibold">
            Redmine Knowledge Agent
          </strong>
          <NavLink href="#/chat" current={route === "chat"}>
            Chat
          </NavLink>
          <NavLink href="#/admin" current={route === "admin"}>
            Sync ダッシュボード
          </NavLink>
        </div>
      </nav>
      {route === "chat" ? <ChatPage /> : <SyncStatusPage />}
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
          ? "rounded bg-slate-100 px-2 py-1 text-sm font-medium text-slate-800"
          : "px-2 py-1 text-sm text-slate-500 hover:text-slate-800"
      }
    >
      {children}
    </a>
  );
}
