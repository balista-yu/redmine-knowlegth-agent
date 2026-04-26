# API 仕様書 (03-api-spec.md)

backend が提供する REST API の仕様。

- ベース URL: `http://localhost:8080`
- 認証: 本フェーズでは無し (ローカル運用前提)
- エンコーディング: UTF-8
- Content-Type: 特記なき場合 `application/json`

## エンドポイント一覧

| Method | Path                  | 用途                              |
| ------ | --------------------- | --------------------------------- |
| POST   | `/api/chat`           | エージェントとのチャット (SSE)     |
| POST   | `/api/sync`           | 差分同期を即時実行                 |
| POST   | `/api/reconcile`      | Reconcile を即時実行               |
| GET    | `/api/sync/status`    | 直近の同期状態を取得               |
| GET    | `/api/sync/runs`      | 同期履歴を取得                     |
| GET    | `/actuator/health`    | ヘルスチェック (Spring 標準)       |

## 1. POST `/api/chat`

エージェントに質問を送り、回答を SSE でストリーム受信する。

### Request

```json
{
  "message": "SSL 証明書のエラーで過去に対応した事例ある?",
  "conversationId": "c-2024-04-26-001"
}
```

| フィールド          | 型      | 必須 | 説明                                                                |
| ------------------- | ------- | ---- | ------------------------------------------------------------------- |
| `message`           | string  | ✓    | ユーザーからの質問 (1 文字以上、4000 文字以下)                        |
| `conversationId`    | string  |      | 会話単位識別子。省略時はサーバ側で発行 (応答ヘッダ `X-Conversation-Id`) |

### Response

- Content-Type: `text/event-stream`

#### イベント `delta` (回答テキストの断片)

```
event: delta
data: {"text": "過去のチケット #128 では..."}
```

LLM がトークン単位で吐き出すテキスト断片を逐次送る。クライアントは連結して表示する。

#### イベント `sources` (引用元情報、回答完了直前に 1 回送出)

```
event: sources
data: {
  "items": [
    {
      "ticketId": 128,
      "subject": "本番Webで証明書期限切れ",
      "url": "http://localhost:3000/issues/128",
      "snippet": "Let's Encryptの自動更新が...",
      "score": 0.83,
      "status": "Closed",
      "tracker": "Bug",
      "projectName": "infra"
    }
  ]
}
```

| フィールド    | 型      | 説明                                |
| ------------- | ------- | ----------------------------------- |
| `ticketId`    | int     | Redmine チケット ID                  |
| `subject`     | string  | チケット件名                         |
| `url`         | string  | Redmine 上のチケット URL             |
| `snippet`     | string  | ヒットしたチャンクの抜粋 (〜200 文字) |
| `score`       | float   | コサイン類似度 (0.0〜1.0)             |
| `status`      | string  | "New" / "Closed" 等                  |
| `tracker`     | string  | "Bug" / "Feature" 等                 |
| `projectName` | string  | プロジェクト名                       |

#### イベント `done`

```
event: done
data: {"conversationId": "c-2024-04-26-001"}
```

#### イベント `error`

```
event: error
data: {"code": "OLLAMA_UNAVAILABLE", "message": "Ollama に接続できませんでした"}
```

#### エラーコード

| コード                  | HTTP 相当 | 内容                                   |
| ----------------------- | --------- | -------------------------------------- |
| `INVALID_REQUEST`       | 400       | message が空 / 長すぎ                  |
| `OLLAMA_UNAVAILABLE`    | 503       | Ollama に接続できない                  |
| `QDRANT_UNAVAILABLE`    | 503       | Qdrant に接続できない                  |
| `INTERNAL`              | 500       | その他                                 |

### サンプル (curl)

```bash
curl -N -X POST http://localhost:8080/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"SSL 証明書のエラーで過去にどう対応した?"}'
```

## 2. POST `/api/sync`

差分同期 (incremental) を即時実行する。

### Request

| クエリ          | 型     | 既定          | 説明                                              |
| --------------- | ------ | ------------- | ------------------------------------------------- |
| `mode`          | string | `incremental` | `incremental` / `full` (full は last_sync_at 無視) |

### Response

#### 200 OK

```json
{
  "runId": 42,
  "kind": "incremental",
  "startedAt": "2024-04-26T10:00:00Z",
  "status": "running"
}
```

#### 409 Conflict

```json
{
  "code": "SYNC_ALREADY_RUNNING",
  "message": "同期が既に実行中です",
  "currentRunId": 41
}
```

## 3. POST `/api/reconcile`

`POST /api/sync` と同形式。`kind` は `"reconcile"` になる。

## 4. GET `/api/sync/status`

```json
{
  "source": "redmine",
  "lastSyncStartedAt": "2024-04-26T10:00:00Z",
  "lastSyncFinishedAt": "2024-04-26T10:00:42Z",
  "lastFullReconcileAt": "2024-04-21T03:00:15Z",
  "lastError": null,
  "ticketsTotal": 1247,
  "currentlyRunning": false,
  "currentRunId": null
}
```

| フィールド               | 型              | 説明                                    |
| ------------------------ | --------------- | --------------------------------------- |
| `source`                 | string          | "redmine" 固定                          |
| `lastSyncStartedAt`      | ISO8601 \| null | 直近の同期開始時刻                      |
| `lastSyncFinishedAt`     | ISO8601 \| null | 直近の同期完了時刻                      |
| `lastFullReconcileAt`    | ISO8601 \| null | 直近 Reconcile 完了時刻                  |
| `lastError`              | string \| null  | 直近エラーメッセージ                    |
| `ticketsTotal`           | int             | Qdrant 上のユニーク ticket_id 数         |
| `currentlyRunning`       | bool            | 現在同期実行中か                         |
| `currentRunId`           | int \| null     | 実行中の run_id                          |

## 5. GET `/api/sync/runs`

### Query

| パラメータ | 型   | 既定 | 説明                                            |
| ---------- | ---- | ---- | ----------------------------------------------- |
| `limit`    | int  | 20   | 取得件数 (上限 100)                              |
| `kind`     | enum |      | `incremental` / `full` / `reconcile` で絞り込み |

### Response 200

```json
{
  "items": [
    {
      "id": 42,
      "kind": "incremental",
      "startedAt": "2024-04-26T10:00:00Z",
      "finishedAt": "2024-04-26T10:00:42Z",
      "status": "success",
      "ticketsFetched": 12,
      "chunksUpserted": 18,
      "chunksSkipped": 6,
      "ticketsDeleted": 0,
      "errorMessage": null
    }
  ],
  "total": 152
}
```

## 6. エラーレスポンス共通フォーマット

非 SSE エンドポイント:

```json
{
  "code": "INVALID_REQUEST",
  "message": "messageは1〜4000文字で指定してください",
  "details": {
    "field": "message",
    "constraint": "size"
  }
}
```

| HTTP | code 例                                                          |
| ---- | ----------------------------------------------------------------- |
| 400  | `INVALID_REQUEST`                                                 |
| 404  | `NOT_FOUND`                                                       |
| 409  | `SYNC_ALREADY_RUNNING`                                            |
| 500  | `INTERNAL`                                                        |
| 503  | `OLLAMA_UNAVAILABLE`, `QDRANT_UNAVAILABLE`, `REDMINE_UNAVAILABLE` |

## 7. データ型: `TicketHit` (Domain Model)

`ragSearch` ツールが返す型。`/api/chat` の `sources` イベントとほぼ同じ構造。

```kotlin
data class TicketHit(
    val ticketId: Int,
    val subject: String,
    val url: String,
    val snippet: String,
    val score: Float,
    val status: String,
    val tracker: String,
    val projectName: String,
    val updatedOn: Instant
)
```

## 8. CORS

開発時のみ `frontend` (5173) からのアクセスを許可。

- 許可オリジン: `http://localhost:5173`
- 許可メソッド: `GET, POST, OPTIONS`
- 許可ヘッダ: `Content-Type, X-Conversation-Id`

本番運用時は CORS 設定を見直すこと (本フェーズではスコープ外)。
