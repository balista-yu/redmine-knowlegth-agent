import {
  afterAll,
  afterEach,
  beforeAll,
  beforeEach,
  describe,
  expect,
  it,
  vi,
} from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { SyncStatusPage } from "./SyncStatusPage";

const STATUS_URL = "/api/sync/status";
const RUNS_URL = "/api/sync/runs";
const SYNC_URL = "/api/sync";
const RECONCILE_URL = "/api/reconcile";

function statusFixture(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    source: "redmine",
    lastSyncStartedAt: "2026-04-29T10:00:00Z",
    lastSyncFinishedAt: "2026-04-29T10:00:42Z",
    lastFullReconcileAt: "2026-04-21T03:00:15Z",
    lastError: null,
    ticketsTotal: 1247,
    currentlyRunning: false,
    currentRunId: null,
    ...overrides,
  };
}

const runsFixture = {
  items: [
    {
      id: 42,
      kind: "incremental" as const,
      startedAt: "2026-04-29T10:00:00Z",
      finishedAt: "2026-04-29T10:00:42Z",
      status: "success" as const,
      ticketsFetched: 12,
      chunksUpserted: 18,
      chunksSkipped: 6,
      ticketsDeleted: 0,
      errorMessage: null,
    },
  ],
  total: 1,
};

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

beforeEach(() => {
  // ポーリングは setInterval なので明示的に fake timers を使うテストもあるが、
  // 本テストは初回 fetch で十分なので real timers を維持する
  vi.useRealTimers();
});

describe("SyncStatusPage", () => {
  it("初回ロードで status と runs を取得して表示する", async () => {
    server.use(
      http.get(STATUS_URL, () => HttpResponse.json(statusFixture())),
      http.get(RUNS_URL, () => HttpResponse.json(runsFixture)),
    );

    render(<SyncStatusPage />);

    // status カードに source / ticketsTotal が出る
    await waitFor(() => {
      expect(screen.getByText("redmine")).toBeInTheDocument();
    });
    expect(screen.getByText("1,247")).toBeInTheDocument();
    // runs テーブルに id=42 が出る
    expect(screen.getByText("42")).toBeInTheDocument();
  });

  it("「今すぐ同期」ボタンを押すと POST /api/sync が呼ばれて refresh される", async () => {
    let postCount = 0;
    server.use(
      http.get(STATUS_URL, () => HttpResponse.json(statusFixture())),
      http.get(RUNS_URL, () => HttpResponse.json(runsFixture)),
      http.post(SYNC_URL, () => {
        postCount += 1;
        return HttpResponse.json({
          runId: 99,
          kind: "incremental",
          startedAt: "2026-04-29T11:00:00Z",
          status: "running",
        });
      }),
    );

    const user = userEvent.setup();
    render(<SyncStatusPage />);
    await waitFor(() => screen.getByText("redmine"));

    await user.click(screen.getByRole("button", { name: "今すぐ同期" }));

    await waitFor(() => expect(postCount).toBe(1));
  });

  it("「Reconcile」ボタンを押すと POST /api/reconcile が呼ばれる", async () => {
    let postCount = 0;
    server.use(
      http.get(STATUS_URL, () => HttpResponse.json(statusFixture())),
      http.get(RUNS_URL, () => HttpResponse.json(runsFixture)),
      http.post(RECONCILE_URL, () => {
        postCount += 1;
        return HttpResponse.json({
          runId: 50,
          kind: "reconcile",
          startedAt: "2026-04-29T12:00:00Z",
          status: "running",
        });
      }),
    );

    const user = userEvent.setup();
    render(<SyncStatusPage />);
    await waitFor(() => screen.getByText("redmine"));

    await user.click(screen.getByRole("button", { name: "Reconcile" }));

    await waitFor(() => expect(postCount).toBe(1));
  });

  it("409 SYNC_ALREADY_RUNNING の場合 alert バナーで currentRunId を表示する", async () => {
    server.use(
      http.get(STATUS_URL, () => HttpResponse.json(statusFixture())),
      http.get(RUNS_URL, () => HttpResponse.json(runsFixture)),
      http.post(SYNC_URL, () =>
        HttpResponse.json(
          {
            code: "SYNC_ALREADY_RUNNING",
            message: "同期が既に実行中です",
            currentRunId: 41,
          },
          { status: 409 },
        ),
      ),
    );

    const user = userEvent.setup();
    render(<SyncStatusPage />);
    await waitFor(() => screen.getByText("redmine"));

    await user.click(screen.getByRole("button", { name: "今すぐ同期" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("run #41");
    });
  });

  it("status の currentlyRunning=true ならボタンが disabled で 実行中 バッジが出る", async () => {
    server.use(
      http.get(STATUS_URL, () =>
        HttpResponse.json(
          statusFixture({ currentlyRunning: true, currentRunId: 99 }),
        ),
      ),
      http.get(RUNS_URL, () => HttpResponse.json(runsFixture)),
    );

    render(<SyncStatusPage />);
    await waitFor(() => screen.getByText("redmine"));

    expect(screen.getByText("実行中 #99")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "今すぐ同期" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Reconcile" })).toBeDisabled();
  });

  it("status fetch が 500 ならエラーバナー表示", async () => {
    server.use(
      http.get(STATUS_URL, () =>
        HttpResponse.json({ code: "INTERNAL", message: "boom" }, { status: 500 }),
      ),
      http.get(RUNS_URL, () => HttpResponse.json(runsFixture)),
    );

    render(<SyncStatusPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("HTTP 500");
    });
  });
});
