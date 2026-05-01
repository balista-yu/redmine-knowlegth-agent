import type { ChatMessage } from "../types";

type MessageListProps = {
  messages: ChatMessage[];
  /** 現在ストリーミング中の assistant 応答 (連結途中の文字列)。空文字列なら非表示。 */
  streamingText: string;
};

/**
 * 会話履歴 + ストリーミング中の応答を順に表示する。
 */
export function MessageList({ messages, streamingText }: MessageListProps) {
  if (messages.length === 0 && streamingText.length === 0) {
    return (
      <p className="rounded-md border border-dashed border-slate-300 bg-white p-6 text-center text-sm text-slate-500">
        質問を入力すると、Redmine のチケットを参照しながら回答します。
      </p>
    );
  }

  return (
    <ul className="flex flex-col gap-3">
      {messages.map((m, idx) => (
        <li
          key={idx}
          data-role={m.role}
          className={
            m.role === "user"
              ? "self-end max-w-[85%] rounded-2xl bg-slate-800 px-4 py-2 text-sm text-white"
              : "self-start max-w-[85%] rounded-2xl bg-white px-4 py-2 text-sm text-slate-800 shadow-sm"
          }
        >
          {m.content}
        </li>
      ))}
      {streamingText.length > 0 && (
        <li
          data-role="assistant-streaming"
          className="self-start max-w-[85%] rounded-2xl bg-white px-4 py-2 text-sm text-slate-800 shadow-sm"
        >
          {streamingText}
          <span className="ml-1 inline-block h-3 w-1 animate-pulse bg-slate-400" />
        </li>
      )}
    </ul>
  );
}
