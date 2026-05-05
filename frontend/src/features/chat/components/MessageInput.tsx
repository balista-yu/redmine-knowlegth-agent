import { useState, type FormEvent, type KeyboardEvent } from "react";

type MessageInputProps = {
  disabled: boolean;
  onSubmit: (message: string) => void;
};

const MAX_LENGTH = 4000;

/**
 * チャット入力フォーム。
 * - `Enter` は改行 (誤送信防止)
 * - `Cmd+Enter` / `Ctrl+Enter` で送信
 * - 「送信」ボタン押下でも送信
 * - 4000 文字制限
 */
export function MessageInput({ disabled, onSubmit }: MessageInputProps) {
  const [text, setText] = useState("");
  const trimmed = text.trim();
  const tooLong = text.length > MAX_LENGTH;
  const canSubmit = !disabled && trimmed.length > 0 && !tooLong;

  function submit() {
    if (!canSubmit) return;
    onSubmit(trimmed);
    setText("");
  }

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    submit();
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    // Cmd/Ctrl+Enter: 送信。それ以外の Enter は textarea 既定 (改行)。
    if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) {
      e.preventDefault();
      submit();
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="rounded-2xl border border-slate-200/80 bg-white p-3 shadow-sm transition focus-within:border-indigo-400 focus-within:shadow-md"
    >
      <textarea
        aria-label="メッセージ入力"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        rows={3}
        placeholder="質問を入力 (改行は Enter、送信は ⌘ / Ctrl+Enter またはボタン)"
        className="w-full resize-none rounded-lg bg-transparent px-2 py-1 text-sm text-slate-800 placeholder-slate-400 focus:outline-none disabled:text-slate-400"
      />
      <div className="mt-2 flex items-center justify-between border-t border-slate-100 pt-2 text-xs">
        <span className="text-slate-500">
          <span className={tooLong ? "font-semibold text-red-600" : "tabular-nums"}>
            {text.length}
          </span>
          <span className="text-slate-400"> / {MAX_LENGTH}</span>
          {tooLong && <span className="ml-2 text-red-600">文字数オーバー</span>}
          <span className="ml-3 hidden text-slate-400 sm:inline">
            送信: ⌘/Ctrl + Enter
          </span>
        </span>
        <button
          type="submit"
          disabled={!canSubmit}
          className="inline-flex items-center gap-1.5 rounded-lg bg-gradient-to-br from-indigo-500 to-purple-600 px-4 py-1.5 text-sm font-medium text-white shadow-sm transition hover:from-indigo-600 hover:to-purple-700 hover:shadow-md disabled:from-slate-300 disabled:to-slate-300 disabled:shadow-none"
        >
          {disabled ? (
            <>
              <span className="inline-block h-3 w-3 animate-spin rounded-full border-2 border-white/40 border-t-white" />
              応答中...
            </>
          ) : (
            <>
              送信
              <span aria-hidden className="text-xs opacity-80">
                ↵
              </span>
            </>
          )}
        </button>
      </div>
    </form>
  );
}
