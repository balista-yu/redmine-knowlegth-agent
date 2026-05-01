import { useState, type FormEvent, type KeyboardEvent } from "react";

type MessageInputProps = {
  disabled: boolean;
  onSubmit: (message: string) => void;
};

const MAX_LENGTH = 4000;

/**
 * チャット入力フォーム。`Enter` で送信、`Shift+Enter` で改行。
 * 4000 文字制限 (docs/03-api-spec.md §1) を超える入力は disabled 状態にする。
 */
export function MessageInput({ disabled, onSubmit }: MessageInputProps) {
  const [text, setText] = useState("");
  const trimmed = text.trim();
  const tooLong = text.length > MAX_LENGTH;
  const canSubmit = !disabled && trimmed.length > 0 && !tooLong;

  function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canSubmit) return;
    onSubmit(trimmed);
    setText("");
  }

  function handleKeyDown(e: KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      if (canSubmit) {
        onSubmit(trimmed);
        setText("");
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <textarea
        aria-label="メッセージ入力"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        rows={3}
        placeholder="質問を入力 (Enter で送信、Shift+Enter で改行)"
        className="w-full resize-none rounded-md border border-slate-300 bg-white px-3 py-2 text-sm shadow-sm focus:border-slate-500 focus:outline-none disabled:bg-slate-100"
      />
      <div className="flex items-center justify-between text-xs text-slate-500">
        <span>
          {text.length} / {MAX_LENGTH}
          {tooLong && (
            <span className="ml-2 text-red-600">文字数オーバーです</span>
          )}
        </span>
        <button
          type="submit"
          disabled={!canSubmit}
          className="rounded-md bg-slate-800 px-4 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:bg-slate-300"
        >
          {disabled ? "応答中..." : "送信"}
        </button>
      </div>
    </form>
  );
}
