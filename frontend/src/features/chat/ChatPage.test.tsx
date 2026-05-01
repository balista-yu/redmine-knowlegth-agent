import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { ChatPage } from "./ChatPage";

// =============================================================================
// MSW で /api/chat の SSE をモックする。
// MSW v2 は ReadableStream を直接 body に渡せるため、`text/event-stream` を組み立てる。
// =============================================================================

function sseStream(...frames: string[]): ReadableStream<Uint8Array> {
  const encoder = new TextEncoder();
  return new ReadableStream({
    start(controller) {
      for (const frame of frames) {
        controller.enqueue(encoder.encode(frame + "\n\n"));
      }
      controller.close();
    },
  });
}

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("ChatPage", () => {
  it("送信すると delta を連結して assistant メッセージとして表示する", async () => {
    server.use(
      http.post("/api/chat", () => {
        return new HttpResponse(
          sseStream(
            'event: delta\ndata: {"text":"Hello, "}',
            'event: delta\ndata: {"text":"world"}',
            'event: done\ndata: {"conversationId":"c-1"}',
          ),
          { headers: { "Content-Type": "text/event-stream" } },
        );
      }),
    );

    const user = userEvent.setup();
    render(<ChatPage />);

    await user.type(screen.getByLabelText("メッセージ入力"), "hi");
    await user.click(screen.getByRole("button", { name: "送信" }));

    // delta が連結されて assistant メッセージとして残ることを確認
    await waitFor(() => {
      expect(screen.getByText("Hello, world")).toBeInTheDocument();
    });
    // user メッセージも表示される
    expect(screen.getByText("hi")).toBeInTheDocument();
  });

  it("sources イベントで引用カード (subject + URL) が表示される", async () => {
    server.use(
      http.post("/api/chat", () => {
        return new HttpResponse(
          sseStream(
            'event: delta\ndata: {"text":"answer"}',
            `event: sources\ndata: ${JSON.stringify({
              items: [
                {
                  ticketId: 128,
                  subject: "tls cert",
                  url: "http://localhost:3000/issues/128",
                  snippet: "Let's Encrypt",
                  score: 0.83,
                  status: "Closed",
                  tracker: "Bug",
                  projectName: "infra",
                },
              ],
            })}`,
            'event: done\ndata: {"conversationId":"c-2"}',
          ),
          { headers: { "Content-Type": "text/event-stream" } },
        );
      }),
    );

    const user = userEvent.setup();
    render(<ChatPage />);

    await user.type(screen.getByLabelText("メッセージ入力"), "tls?");
    await user.click(screen.getByRole("button", { name: "送信" }));

    await waitFor(() => {
      expect(screen.getByText("tls cert")).toBeInTheDocument();
    });
    expect(screen.getByText("#128")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: /tls cert/ }),
    ).toHaveAttribute("href", "http://localhost:3000/issues/128");
  });

  it("error イベントを受信すると赤バナー (alert) が表示される", async () => {
    server.use(
      http.post("/api/chat", () => {
        return new HttpResponse(
          sseStream(
            'event: error\ndata: {"code":"OLLAMA_UNAVAILABLE","message":"ollama unreachable"}',
          ),
          { headers: { "Content-Type": "text/event-stream" } },
        );
      }),
    );

    const user = userEvent.setup();
    render(<ChatPage />);

    await user.type(screen.getByLabelText("メッセージ入力"), "hi");
    await user.click(screen.getByRole("button", { name: "送信" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent(
        "OLLAMA_UNAVAILABLE",
      );
    });
  });

  it("送信中は送信ボタンが disabled になり 応答中... 表示", async () => {
    // close を後から呼べるようにコントローラを外部に持ち出す
    const controllerRef: { current: ReadableStreamDefaultController<Uint8Array> | null } = {
      current: null,
    };
    server.use(
      http.post("/api/chat", () => {
        const stream = new ReadableStream<Uint8Array>({
          start(controller) {
            const encoder = new TextEncoder();
            controller.enqueue(
              encoder.encode('event: delta\ndata: {"text":"hi"}\n\n'),
            );
            // close せず controller を保持しておく (テスト末尾で close)
            controllerRef.current = controller;
          },
        });
        return new HttpResponse(stream, {
          headers: { "Content-Type": "text/event-stream" },
        });
      }),
    );

    const user = userEvent.setup();
    render(<ChatPage />);

    await user.type(screen.getByLabelText("メッセージ入力"), "hi");
    await user.click(screen.getByRole("button", { name: "送信" }));

    // streaming 中は "応答中..." ボタンに切り替わり disabled
    const button = await screen.findByRole("button", { name: "応答中..." });
    expect(button).toBeDisabled();

    // クリーンアップ: ストリームを終了させる
    controllerRef.current?.close();
  });

  it("HTTP エラー (500) でエラーバナー表示", async () => {
    server.use(
      http.post("/api/chat", () => {
        return new HttpResponse("server boom", { status: 500 });
      }),
    );
    // console.error を抑制 (React が unhandled error を出力するため)
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    const user = userEvent.setup();
    render(<ChatPage />);
    await user.type(screen.getByLabelText("メッセージ入力"), "hi");
    await user.click(screen.getByRole("button", { name: "送信" }));

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("HTTP 500");
    });
    consoleError.mockRestore();
  });
});
