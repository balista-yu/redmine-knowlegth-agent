import { useCallback, useReducer, useRef } from "react";
import { streamChat, ChatStreamHttpError } from "./api/chatStream";
import { MessageInput } from "./components/MessageInput";
import { MessageList } from "./components/MessageList";
import { SourcesPanel } from "./components/SourcesPanel";
import type { ChatMessage, ChatStreamEvent, TicketHit } from "./types";

type ChatState = {
  messages: ChatMessage[];
  streamingText: string;
  sources: TicketHit[];
  conversationId: string | null;
  status: "idle" | "streaming";
  error: string | null;
};

const initialState: ChatState = {
  messages: [],
  streamingText: "",
  sources: [],
  conversationId: null,
  status: "idle",
  error: null,
};

type ChatAction =
  | { type: "SUBMIT"; userMessage: string }
  | { type: "DELTA"; text: string }
  | { type: "SOURCES"; items: TicketHit[] }
  | { type: "DONE"; conversationId: string }
  | { type: "ERROR"; message: string };

function reducer(state: ChatState, action: ChatAction): ChatState {
  switch (action.type) {
    case "SUBMIT":
      return {
        ...state,
        messages: [
          ...state.messages,
          { role: "user", content: action.userMessage },
        ],
        streamingText: "",
        sources: [],
        status: "streaming",
        error: null,
      };
    case "DELTA":
      return { ...state, streamingText: state.streamingText + action.text };
    case "SOURCES":
      return { ...state, sources: action.items };
    case "DONE": {
      const finalText = state.streamingText;
      const nextMessages: ChatMessage[] = finalText.length
        ? [...state.messages, { role: "assistant", content: finalText }]
        : state.messages;
      return {
        ...state,
        messages: nextMessages,
        streamingText: "",
        conversationId: action.conversationId,
        status: "idle",
      };
    }
    case "ERROR":
      return { ...state, status: "idle", error: action.message };
    default:
      return state;
  }
}

/**
 * チャット画面のトップ component (F-04)。会話履歴 + 入力 + 引用パネル + エラーバナー。
 *
 * SSE は `streamChat` (`fetch + ReadableStream`) で受信し、`AgentEvent` の
 * 種別ごとに `useReducer` で状態を更新する。
 */
export function ChatPage() {
  const [state, dispatch] = useReducer(reducer, initialState);
  const abortRef = useRef<AbortController | null>(null);

  const handleSubmit = useCallback(
    (message: string) => {
      // 既存ストリームがあれば abort (理論上発生しないが安全のため)
      abortRef.current?.abort();
      const controller = new AbortController();
      abortRef.current = controller;

      dispatch({ type: "SUBMIT", userMessage: message });

      void (async () => {
        try {
          for await (const event of streamChat(
            message,
            state.conversationId ?? undefined,
            controller.signal,
          )) {
            applyEvent(event, dispatch);
          }
        } catch (err) {
          if (controller.signal.aborted) return;
          dispatch({ type: "ERROR", message: errorMessage(err) });
        }
      })();
    },
    [state.conversationId],
  );

  return (
    <div className="grid min-h-screen grid-cols-1 gap-6 bg-slate-50 px-6 py-6 lg:grid-cols-[minmax(0,1fr)_320px]">
      <section className="flex flex-col gap-4">
        <h1 className="text-xl font-semibold text-slate-800">
          Redmine Knowledge Agent
        </h1>
        {state.error && (
          <div
            role="alert"
            className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700"
          >
            {state.error}
          </div>
        )}
        <div className="flex-1 rounded-md bg-slate-100 p-4">
          <MessageList
            messages={state.messages}
            streamingText={state.streamingText}
          />
        </div>
        <MessageInput
          disabled={state.status === "streaming"}
          onSubmit={handleSubmit}
        />
      </section>
      <SourcesPanel items={state.sources} />
    </div>
  );
}

function applyEvent(
  event: ChatStreamEvent,
  dispatch: React.Dispatch<ChatAction>,
) {
  switch (event.type) {
    case "delta":
      dispatch({ type: "DELTA", text: event.text });
      break;
    case "sources":
      dispatch({ type: "SOURCES", items: event.items });
      break;
    case "done":
      dispatch({ type: "DONE", conversationId: event.conversationId });
      break;
    case "error":
      dispatch({ type: "ERROR", message: `${event.code}: ${event.message}` });
      break;
  }
}

function errorMessage(err: unknown): string {
  if (err instanceof ChatStreamHttpError) {
    return `通信エラー (HTTP ${err.status})`;
  }
  if (err instanceof Error) return err.message;
  return "通信エラーが発生しました";
}
