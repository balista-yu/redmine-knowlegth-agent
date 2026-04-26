---
paths:
  - "frontend/**"
---

# Frontend ルール (path-scoped)

このルールは `frontend/` 配下のファイル編集時に自動でロードされる。

## 技術スタック

- React 18 + TypeScript (`strict: true`)
- Vite + TailwindCSS
- Vitest + Testing Library + MSW
- ESLint + `@typescript-eslint`

## ディレクトリ構成

```
frontend/src/
├── features/                  # 機能単位
│   ├── chat/
│   │   ├── ChatPage.tsx
│   │   ├── components/        # 機能内のコンポーネント
│   │   ├── hooks/             # 機能固有 hook
│   │   └── api/               # 機能の API クライアント (chatStream.ts 等)
│   └── admin/
│       └── SyncStatusPage.tsx
├── shared/                    # 機能横断のもの
│   ├── components/            # 汎用 UI
│   ├── hooks/
│   └── api/                   # 共通 API ユーティリティ
├── App.tsx
└── main.tsx
```

## TypeScript ルール

- **`any` 禁止**: 型不明な場合は `unknown` を使い narrowing する
- **`strict: true`** を維持 (`strictNullChecks`, `noImplicitAny` 含む)
- **named export 優先**: default export は避ける (auto-import で名前ブレを防ぐ)
- **interface vs type**: 拡張するなら `interface`、union type なら `type`
- **enum 禁止**: 文字列リテラル union を使う (`type Status = "running" | "success"`)

## React コンポーネント

- **関数コンポーネント + hooks** のみ (class component 禁止)
- **props 型は `Props` 命名**: `type ChatPageProps = { ... }`
- **副作用は `useEffect` の中だけ**
- **状態管理**: 機能内は `useState` / `useReducer`、機能横断は React Context (本フェーズは Redux 等不使用)

## SSE 受信パターン

`POST /api/chat` を SSE で受けるため、`EventSource` ではなく `fetch + ReadableStream` を使う。

```ts
async function* streamChat(message: string): AsyncGenerator<ChatEvent> {
  const res = await fetch("/api/chat", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ message }),
  });
  if (!res.body) throw new Error("No body");
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  while (true) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    // SSE 形式 ("event: foo\ndata: ...\n\n") をパース
    // ...
    yield event;
  }
}
```

API 仕様は `docs/03-api-spec.md` §1 参照。

## スタイリング (TailwindCSS)

- **Tailwind core utilities のみ使用**: 独自 CSS は最小限
- **`@apply` を多用しない**: 再利用が必要なパターンはコンポーネント化
- **クラス名はソート順**: `prettier-plugin-tailwindcss` 推奨

## API クライアント

- `frontend/src/features/<機能>/api/` または `shared/api/` に集約
- `fetch` ラッパで `Content-Type: application/json` をデフォルト化
- エラーハンドリングは API レスポンスの `code` フィールド (例: `OLLAMA_UNAVAILABLE`) で分岐 (`docs/03-api-spec.md` §6 参照)

## テスト

- ファイル配置: `<コンポーネント名>.test.tsx` (隣接) または `__tests__/`
- MSW (`msw`) で API モック
- Testing Library のクエリは `getByRole` を優先、`getByTestId` は最後の手段
- `userEvent` でインタラクション (fireEvent より推奨)

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

describe("ChatPage", () => {
  it("送信すると delta を連結表示", async () => {
    render(<ChatPage />);
    await userEvent.type(screen.getByRole("textbox"), "Hello");
    await userEvent.click(screen.getByRole("button", { name: /送信/i }));
    expect(await screen.findByText(/Hello/)).toBeInTheDocument();
  });
});
```

## やってはいけない

- `any` 型を使う
- `console.log` を残してコミット (デバッグ用なら削除)
- インラインスタイル (`style={{ ... }}`) を多用 (Tailwind utility を使う)
- グローバル状態を window/global オブジェクトで管理
- 直接 fetch を component 内に書き散らす (api/ に集約)
