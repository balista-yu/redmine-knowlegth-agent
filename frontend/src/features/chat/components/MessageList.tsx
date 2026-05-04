import type { ChatMessage } from "../types";

type MessageListProps = {
  messages: ChatMessage[];
  /** 現在ストリーミング中の assistant 応答 (連結途中の文字列)。空文字列なら非表示。 */
  streamingText: string;
};

/**
 * 会話履歴 + ストリーミング中の応答をアバター付きで順に表示する。
 */
export function MessageList({ messages, streamingText }: MessageListProps) {
  if (messages.length === 0 && streamingText.length === 0) {
    return (
      <div className="flex h-full min-h-[40vh] flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-slate-300 bg-white/60 p-10 text-center">
        <div className="text-4xl" aria-hidden>
          💬
        </div>
        <p className="text-sm font-medium text-slate-700">
          質問を入力すると、Redmine のチケットを参照しながら回答します。
        </p>
        <p className="text-xs text-slate-500">
          例: <span className="rounded bg-slate-100 px-1.5 py-0.5 font-mono">
            SSL 証明書の期限切れで過去にどう対処した?
          </span>
        </p>
      </div>
    );
  }

  return (
    <ul className="flex flex-col gap-4 px-1">
      {messages.map((m, idx) => (
        <MessageBubble
          key={idx}
          role={m.role}
          content={m.content}
          dataRole={m.role}
        />
      ))}
      {streamingText.length > 0 && (
        <MessageBubble
          role="assistant"
          content={streamingText}
          dataRole="assistant-streaming"
          streaming
        />
      )}
    </ul>
  );
}

function MessageBubble({
  role,
  content,
  dataRole,
  streaming = false,
}: {
  role: "user" | "assistant";
  content: string;
  dataRole: string;
  streaming?: boolean;
}) {
  const isUser = role === "user";
  return (
    <li
      data-role={dataRole}
      className={`flex items-start gap-3 ${isUser ? "flex-row-reverse" : ""}`}
    >
      <Avatar role={role} />
      <div
        className={`max-w-[80%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed shadow-sm ${
          isUser
            ? "bg-gradient-to-br from-indigo-500 to-purple-600 text-white"
            : "border border-slate-200 bg-white text-slate-800"
        }`}
      >
        <p className="whitespace-pre-wrap">{content}</p>
        {streaming && (
          <span
            aria-hidden
            className="ml-1 inline-block h-3 w-1 animate-pulse rounded-sm bg-slate-400 align-middle"
          />
        )}
      </div>
    </li>
  );
}

function Avatar({ role }: { role: "user" | "assistant" }) {
  const isUser = role === "user";
  return (
    <div
      aria-hidden
      className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-medium shadow-sm ${
        isUser
          ? "bg-slate-800 text-white"
          : "bg-gradient-to-br from-indigo-100 to-purple-100 text-indigo-700"
      }`}
    >
      {isUser ? "あ" : "AI"}
    </div>
  );
}
