import type {
  ChatStreamEvent,
  ChatDeltaEvent,
  ChatSourcesEvent,
  ChatDoneEvent,
  ChatErrorEvent,
  TicketHit,
} from "../types";

/**
 * `POST /api/chat` を `fetch + ReadableStream` で受信し、SSE フレームを 1 件ずつ
 * `ChatStreamEvent` の AsyncGenerator として yield する。
 *
 * .claude/rules/frontend.md の推奨パターン (`EventSource` ではなく `fetch` を使う)。
 * SSE 形式は "event: <name>\ndata: <json>\n\n" のフレームを期待する
 * (docs/openapi.yaml (POST /api/chat) と Spring の `ServerSentEvent` フォーマット)。
 *
 * 多重送信防止は呼び出し側 (`ChatPage`) のボタン disabled で完結するため、
 * AbortSignal は受け取らない (Node 24 の undici fetch が jsdom の AbortSignal を
 * `instanceof` で拒否するテスト互換性問題を回避するため)。
 */
export async function* streamChat(
  message: string,
  conversationId?: string,
): AsyncGenerator<ChatStreamEvent> {
  const res = await fetch("/api/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
    },
    body: JSON.stringify({ message, conversationId: conversationId ?? null }),
  });
  if (!res.ok) {
    throw new ChatStreamHttpError(res.status, await safeReadText(res));
  }
  if (!res.body) {
    throw new Error("Response body is null");
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  // ストリーム終端まで読み取り、フレーム単位 (空行区切り) でパースする
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let frameEnd = buffer.indexOf("\n\n");
    while (frameEnd !== -1) {
      const frame = buffer.slice(0, frameEnd);
      buffer = buffer.slice(frameEnd + 2);
      const event = parseSseFrame(frame);
      if (event) yield event;
      frameEnd = buffer.indexOf("\n\n");
    }
  }
  // 最終フラッシュ: \n\n で終わらない末尾フレームを救う
  if (buffer.trim().length > 0) {
    const event = parseSseFrame(buffer);
    if (event) yield event;
  }
}

/**
 * SSE 1 フレーム (`event: foo\ndata: {...}`) を `ChatStreamEvent` にパースする。
 * 不明な event 名や不正な JSON は `null` を返してスキップする。
 */
function parseSseFrame(frame: string): ChatStreamEvent | null {
  let event = "";
  const dataLines: string[] = [];
  for (const line of frame.split("\n")) {
    const trimmed = line.trim();
    if (trimmed.length === 0 || trimmed.startsWith(":")) continue;
    if (trimmed.startsWith("event:")) {
      event = trimmed.slice("event:".length).trim();
    } else if (trimmed.startsWith("data:")) {
      dataLines.push(trimmed.slice("data:".length).trim());
    }
  }
  if (event.length === 0 || dataLines.length === 0) return null;
  const dataJson = dataLines.join("\n");
  try {
    const data: unknown = JSON.parse(dataJson);
    return mapEvent(event, data);
  } catch {
    return null;
  }
}

function mapEvent(event: string, data: unknown): ChatStreamEvent | null {
  if (typeof data !== "object" || data === null) return null;
  const obj = data as Record<string, unknown>;
  switch (event) {
    case "delta":
      return typeof obj.text === "string"
        ? ({ type: "delta", text: obj.text } satisfies ChatDeltaEvent)
        : null;
    case "sources":
      return Array.isArray(obj.items)
        ? ({
            type: "sources",
            items: obj.items.filter(isTicketHit),
          } satisfies ChatSourcesEvent)
        : null;
    case "done":
      return typeof obj.conversationId === "string"
        ? ({
            type: "done",
            conversationId: obj.conversationId,
          } satisfies ChatDoneEvent)
        : null;
    case "error":
      return typeof obj.code === "string" && typeof obj.message === "string"
        ? ({
            type: "error",
            code: obj.code,
            message: obj.message,
          } satisfies ChatErrorEvent)
        : null;
    default:
      return null;
  }
}

function isTicketHit(value: unknown): value is TicketHit {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.ticketId === "number" &&
    typeof v.subject === "string" &&
    typeof v.url === "string" &&
    typeof v.snippet === "string" &&
    typeof v.score === "number"
  );
}

async function safeReadText(res: Response): Promise<string> {
  try {
    return await res.text();
  } catch {
    return "";
  }
}

export class ChatStreamHttpError extends Error {
  constructor(
    public readonly status: number,
    public readonly body: string,
  ) {
    super(`Chat stream HTTP ${status}`);
    this.name = "ChatStreamHttpError";
  }
}
