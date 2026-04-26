---
name: sync-status
description: 起動中の backend に問い合わせて Redmine 同期状態を確認する Skill。/sync-status で呼び出す。最終同期時刻・直近実行履歴・エラー有無を一覧表示する。希望時は task sync / task reconcile も実行する。
allowed-tools: Bash(curl:*), Bash(task sync:*), Bash(task reconcile:*)
---

# 同期状態の確認

backend (http://localhost:8080) に問い合わせて現在の同期状態を表示する。

## 実行

```bash
# 同期状態
curl -sS http://localhost:8080/api/sync/status | jq .

# 直近の履歴 (最新 10 件)
curl -sS 'http://localhost:8080/api/sync/runs?limit=10' | jq .
```

## 必要に応じて手動操作

ユーザーが希望する場合のみ実行:

- 即時同期: `task sync` (または `curl -X POST http://localhost:8080/api/sync`)
- 即時 Reconcile: `task reconcile`

## 出力形式

```markdown
## 同期状態

### 現状
- 最終同期開始: <ISO8601>
- 最終同期完了: <ISO8601>
- 直近エラー: <あり/なし。あればメッセージ>
- Qdrant 上のチケット数: N
- 現在実行中: <はい/いいえ>

### 直近の履歴
| ID | kind | 開始 | 完了 | 状態 | fetched | upserted | skipped | deleted |
| -- | ---- | ---- | ---- | ---- | ------- | -------- | ------- | ------- |
| 42 | incremental | ... | ... | success | 12 | 18 | 6 | 0 |
| 41 | incremental | ... | ... | success | 5  | 7  | 3 | 0 |

### 観察
- (パターンや異常があれば指摘)
```
