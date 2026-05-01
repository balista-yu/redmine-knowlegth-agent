import { render, screen } from "@testing-library/react";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { App } from "./App";

// SyncStatusPage が起動時に /api/sync/status と /api/sync/runs を fetch するため
// テスト中は MSW で 200 を返す (ダッシュボード単体テストでは別途検証)
const server = setupServer(
  http.get("/api/sync/status", () =>
    HttpResponse.json({
      source: "redmine",
      lastSyncStartedAt: null,
      lastSyncFinishedAt: null,
      lastFullReconcileAt: null,
      lastError: null,
      ticketsTotal: 0,
      currentlyRunning: false,
      currentRunId: null,
    }),
  ),
  http.get("/api/sync/runs", () => HttpResponse.json({ items: [], total: 0 })),
);

beforeAll(() => server.listen({ onUnhandledRequest: "warn" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("App", () => {
  it("ナビゲーションにアプリ名が表示される", () => {
    render(<App />);
    // ChatPage h1 と nav の strong の 2 箇所に出る
    expect(screen.getAllByText("Redmine Knowledge Agent").length).toBeGreaterThan(0);
  });

  it("デフォルトは Chat ページ (メッセージ入力欄が見える)", () => {
    window.location.hash = "";
    render(<App />);
    expect(screen.getByLabelText("メッセージ入力")).toBeInTheDocument();
  });
});
