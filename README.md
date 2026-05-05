# Redmine Knowledge Agent

Redmine のチケットを RAG ナレッジ源とする AI エージェント (Kotlin / Spring Boot 4 / Koog 0.8 + React 19 + Qdrant + PostgreSQL + Ollama)。

## 構成 (一行サマリ)

| 役割              | 技術                                                |
| ----------------- | --------------------------------------------------- |
| Backend           | Kotlin 2.3 / Spring Boot 4.0 / Koog 0.8 (RAG Agent)  |
| Frontend          | React 19 / Vite 7 / Tailwind 4 / Vitest 3           |
| ベクトル DB        | Qdrant (gRPC)                                       |
| メタデータ DB      | PostgreSQL 16 (Flyway 管理)                          |
| Redmine           | Redmine 5 + MySQL 8 (Docker)                         |
| LLM / Embedding   | Ollama (本リポジトリ管理外、ホストで起動)            |
| オーケストレーション | Docker Compose / Taskfile                            |

詳細アーキテクチャは [`docs/01-design.md`](./docs/01-design.md) を参照。

## 必要なもの

| ツール          | 推奨バージョン | 備考                                             |
| --------------- | -------------- | ------------------------------------------------ |
| Docker          | 24+            | Docker Compose v2 同梱                            |
| [Task]          | 3.x            | `task` コマンド (Taskfile.yaml ランナー)          |
| [Ollama]        | latest         | ホストで起動。LLM + embedding モデルを pull 済み |
| Git             | 2.x            |                                                  |

[Task]: https://taskfile.dev
[Ollama]: https://ollama.com

> ホストに **JDK / Node / npm は不要**。すべて Docker Compose の中で実行する。

### Ollama 用モデルの取得 (ホスト側で 1 回だけ)

```bash
ollama pull qwen2.5:7b           # チャット LLM
ollama pull nomic-embed-text     # Embedding
```

別モデルを使いたい場合は `.env` の `OLLAMA_LLM_MODEL` / `OLLAMA_EMBED_MODEL` を上書きする。

### Ollama を別リポジトリの compose で動かしている場合

ホストインストール (`host.docker.internal` 経由) ではなく Ollama を別 compose
プロジェクトで起動している場合は、`compose.override.yaml.example` をコピーして
ネットワークを共有する:

```bash
cp compose.override.yaml.example compose.override.yaml
# 中の external network 名を Ollama 側のものに書き換え
# OLLAMA_BASE_URL=http://ollama:11434 を .env に設定
```

`compose.override.yaml` は git 管理外 (`.gitignore` 済) なので環境ごとの差は本ファイルに閉じ込める。

## 環境構築 (初回セットアップ)

### 1. リポジトリ取得

```bash
git clone https://github.com/balista-yu/redmine-knowlegth-agent.git
cd redmine-knowlegth-agent
```

### 2. 環境変数ファイル作成

```bash
cp .env.example .env
```

`REDMINE_API_KEY` と `APP_DB_PASSWORD` は手順 4・5 で値が決まるため、ここでは空のままで OK。

### 3. コンテナ起動

```bash
task up           # = docker compose up -d
```

初回は Spring Boot / npm install / Redmine 初期化に **2〜3 分** かかる。

`task logs` で進捗確認:

```bash
task logs-app       # backend のログ
task logs-frontend  # frontend のログ
```

各サービスの待ち受け先:

| サービス  | URL / Port                                      |
| --------- | ----------------------------------------------- |
| Frontend  | <http://localhost:5173>                          |
| Backend   | <http://localhost:8080> (`/actuator/health` で UP 確認可) |
| Redmine   | <http://localhost:3000>                          |
| Qdrant    | localhost:6333 (HTTP) / localhost:6334 (gRPC)    |
| Postgres  | localhost:5432                                   |

### 4. Redmine 初期セットアップ + API キー取得

1. ブラウザで <http://localhost:3000> を開く
2. `admin` / `admin` でログイン → 強制パスワード変更
3. 右上 **個人設定** → 右ペイン **APIアクセスキー** → **[表示]** をクリック
4. 40 桁のキーをコピーして `.env` の `REDMINE_API_KEY` に貼り付け

### 5. PostgreSQL パスワード設定

`.env` の `APP_DB_PASSWORD` を任意の値に設定。
`compose.yaml` の `app-db` サービスは初回起動時にこの値で初期化される。
変更後は `task down -v && task up` で volume を作り直すと反映される。

### 6. サンプルデータ投入

```bash
export REDMINE_API_KEY=$(grep '^REDMINE_API_KEY=' .env | cut -d= -f2)
task seed-redmine
```

`ai-agent-test` プロジェクトに 12 件のサンプルチケット (Bug / Feature / Support 混在、journal 付き) が作成される。詳細は [`scripts/README.md`](./scripts/README.md)。

### 7. 初回同期実行

```bash
task sync                          # POST /api/sync
curl http://localhost:8080/api/sync/status  # 状態確認
```

`tickets_total: 12` 程度になれば成功。Qdrant に embedding 入りで保存されている。
フロントエンドの Sync ダッシュボード (`http://localhost:5173/#/admin`) からも同じ操作・確認ができる。

### 8. 動作確認

- フロント Chat: <http://localhost:5173> で質問入力 → 引用付き回答
- フロント Sync ダッシュボード: <http://localhost:5173/#/admin>
- API: 機械可読仕様 [`docs/openapi.yaml`](./docs/openapi.yaml) / Swagger UI <http://localhost:8080/swagger-ui.html>

## 主要コマンド

`task` 引数なしで一覧表示。

| コマンド             | 内容                                                   |
| -------------------- | ------------------------------------------------------ |
| `task up`            | 全コンテナ起動                                         |
| `task down`          | 全コンテナ停止 (volume は残る)                         |
| `task build`         | Docker イメージのビルド                                |
| `task logs`          | 全サービスのログ追跡                                   |
| `task logs-app`      | backend のみログ追跡                                   |
| `task logs-frontend` | frontend のみログ追跡                                  |
| `task test`          | backend + frontend の全テスト                          |
| `task test-be`       | backend のみ (Kotest 6 + JUnit Spring + ArchUnit)       |
| `task test-fe`       | frontend のみ (Vitest + RTL + MSW)                      |
| `task lint`          | backend + frontend の lint                             |
| `task lint-be`       | Spotless (ktlint) + Detekt 2.x                         |
| `task lint-fe`       | ESLint v9 (flat config)                                |
| `task sync`          | 差分同期トリガー (`POST /api/sync`)                    |
| `task reconcile`     | Reconciliation トリガー (`POST /api/reconcile`)        |
| `task seed-redmine`  | Redmine にサンプルチケット投入 ([scripts/README.md])    |

[scripts/README.md]: ./scripts/README.md

## トラブルシュート

### `task up` 後に backend が UP にならない

- `task logs-app` でスタックトレース確認
- 多くの場合 Postgres / Redmine / Qdrant の起動待ち。`compose.yaml` の
  `depends_on: condition: service_healthy` で順序制御しているが、初回は
  Redmine の Rails 起動だけで 60 秒程度かかる
- それでも UP しない場合は `.env` の `APP_DB_PASSWORD` 未設定や `REDMINE_API_KEY` 不正
  が疑わしい

### Ollama に接続できない (Linux)

`compose.yaml` の各サービスに `extra_hosts: host-gateway` を仕込んであるので、
コンテナから `host.docker.internal` でホストを参照できる。Ollama がホストの
`11434` で listen していれば疎通するはず。

```bash
# ホストでの listen 確認
curl http://localhost:11434/api/tags
# コンテナ内からの疎通確認
docker compose exec backend wget -qO- http://host.docker.internal:11434/api/tags
```

### サンプルデータをやり直したい

```bash
task down -v       # volume 含めて削除
task up            # 起動 → 再度 admin パスワード設定 → API キー取得
task seed-redmine  # 再投入
```

## 開発フロー

1. `docs/02-tasks.md` から次のタスクを選ぶ
2. Claude Code で `/task <タスクID>` を実行 (例: `/task T-2-2`)
3. テスト先行 → 実装 → `task lint && task test` グリーン確認
4. AI が Conventional Commits 形式でコミット → 作業ブランチへ push
5. PR レビュー → main にマージ

詳細は [`CLAUDE.md`](./CLAUDE.md) と [`.claude/rules/`](./.claude/rules/) を参照。

## ドキュメント

| ファイル                                              | 内容                                                     |
| ----------------------------------------------------- | -------------------------------------------------------- |
| [`docs/README.md`](./docs/README.md)                   | プロジェクト概要 (詳細版)                                |
| [`docs/01-design.md`](./docs/01-design.md)             | 設計書 (アーキテクチャ・データモデル・シーケンス・テスト戦略) |
| [`docs/02-tasks.md`](./docs/02-tasks.md)               | タスク分解書                                             |
| [`docs/openapi.yaml`](./docs/openapi.yaml)             | REST API 仕様 (springdoc-openapi で自動生成)              |
| [`docs/04-claude-code-guide.md`](./docs/04-claude-code-guide.md) | Claude Code Skills 利用ガイド                  |
| [`scripts/README.md`](./scripts/README.md)             | seed スクリプト手順                                       |
| [`CLAUDE.md`](./CLAUDE.md)                             | Claude Code 用プロジェクト規約                            |
