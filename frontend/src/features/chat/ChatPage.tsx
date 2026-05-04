import { useCallback, useReducer } from "react";
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

  const handleSubmit = useCallback(
    (message: string) => {
      // 多重送信は MessageInput 側で送信ボタン disabled にして防いでいるため、
      // ここでは AbortController による途中キャンセルは行わない。
      dispatch({ type: "SUBMIT", userMessage: message });

      void (async () => {
        try {
          for await (const event of streamChat(
            message,
            state.conversationId ?? undefined,
          )) {
            applyEvent(event, dispatch);
          }
        } catch (err) {
          dispatch({ type: "ERROR", message: errorMessage(err) });
        }
      })();
    },
    [state.conversationId],
  );

  return (
    <div className="grid min-h-[calc(100vh-3.5rem)] grid-cols-1 gap-6 px-6 py-6 lg:grid-cols-[minmax(0,1fr)_340px]">
      <section className="flex min-h-0 flex-col gap-4">
        {state.error && (
          <div
            role="alert"
            className="flex items-start gap-2 rounded-xl border border-red-200 bg-red-50/80 px-4 py-2.5 text-sm text-red-700 shadow-sm"
          >
            <span aria-hidden>⚠</span>
            <span className="leading-relaxed">{state.error}</span>
          </div>
        )}
        <div className="flex-1 overflow-y-auto rounded-2xl bg-gradient-to-br from-slate-50 to-white p-4 ring-1 ring-slate-200/60">
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
