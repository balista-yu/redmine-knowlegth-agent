# Redmine Knowledge Agent — Documentation

Redmine のチケットを RAG ナレッジ源とする AI エージェントの設計ドキュメント群。

## ドキュメント一覧

| ファイル                  | 内容                                                |
| ------------------------- | --------------------------------------------------- |
| `README.md` (本ファイル)  | プロジェクト概要・クイックスタート                    |
| `01-design.md`            | 設計書: アーキテクチャ・データモデル・シーケンス・テスト戦略 |
| `02-tasks.md`             | タスク分解書: 実装フェーズと粒度の細かいタスク         |
| `openapi.yaml`            | REST API 仕様 (springdoc-openapi で自動生成。Swagger UI: http://localhost:8080/swagger-ui.html) |
| `04-claude-code-guide.md` | Claude Code 利用ガイド: Skills・rules 概要              |

## Claude Code 連携

本プロジェクトは Claude Code での実装を前提に設計されている。プロジェクトルートの `CLAUDE.md` と `.claude/` 配下に、Claude Code 用の設定・ルール・Skills を配置している。

- `CLAUDE.md`: 不変ルール (毎セッション読み込まれる)
- `.claude/rules/*.md`: path-scoped 詳細ルール (該当ファイル作業時にロード)
- `.claude/skills/<name>/SKILL.md`: Skills (`/task`, `/review`, `/fix` 等の明示呼び出し / description マッチで自動起動)
- `.claude/settings.json`: 権限設定

詳細は `04-claude-code-guide.md` 参照。

## 概要

過去チケットの description / journals(コメント) を定期的にバッチ取得し、ローカル LLM (Ollama) で生成したベクトルを Qdrant に格納。ユーザーからの質問に対して RAG (Retrieval-Augmented Generation) で類似事例を引きながら回答する。

```
[User] -- 質問 --> [Frontend (React)] -- HTTP/SSE --> [Backend (Spring Boot + Koog)]
                                                          │
                                                          ├── ragSearch() -----> [Qdrant]
                                                          ├── chat/embed -----> [Ollama (外部)]
                                                          └── @Scheduled 同期 -> [Redmine REST API]
```

## ユースケース

- **ナレッジ QA**: 「過去に SSL 証明書エラーが出たチケットでの解決方法は?」のような質問に対し、類似チケットを検索して回答 + 引用元 (チケット ID/URL) を提示する

## 設計原則

詳細は `CLAUDE.md` と `.claude/rules/architecture.md` を参照。要約:

- **オニオンアーキテクチャ** (4 層): Domain Model / Domain Services / Application Services / Infrastructure
- **依存方向は外→内のみ**: Domain は外側のフレームワークを import しない (ArchUnit で機械検査)
- **依存性逆転 (DIP)**: 外部システムへのアクセスは Domain で interface 定義、Infrastructure で実装
- **テストファースト寄り**: Domain / Application は単体テスト必須。Infrastructure は Testcontainers
- **小さく作って広げる**: 個人・小チーム検証スケール (チケット〜数千件) 前提

## 技術スタック

| Layer        | Technology                                              |
| ------------ | ------------------------------------------------------- |
| Frontend     | React 18 + TypeScript + Vite + TailwindCSS              |
| Backend      | Kotlin + Spring Boot 3.5 + Koog 0.7.3                   |
| LLM / Embed  | Ollama (外部 repo で管理)                                |
| Vector Store | Qdrant (Docker)                                         |
| Knowledge    | Redmine (Docker, MySQL バックエンド)                     |
| Sync Meta DB | PostgreSQL (Docker)                                     |
| Test (BE)    | JUnit 5 + MockK + Kotest assertions + Testcontainers + ArchUnit |
| Test (FE)    | Vitest + Testing Library + MSW                          |
| Infra        | Docker Compose + Task (Taskfile.yaml)                   |
| 静的解析     | ktlint + detekt (BE) / ESLint + TypeScript strict (FE)   |

## クイックスタート

### 前提

- Docker / Docker Compose
- [Task](https://taskfile.dev/) (タスクランナー)
- [Claude Code](https://docs.claude.com/en/docs/claude-code/overview)
- Ollama (別 repo でセットアップ済み、`http://host.docker.internal:11434` で到達可能)
  - 必要モデル: LLM 用 (例: `qwen2.5:7b`) + Embed 用 (`nomic-embed-text`)

### セットアップ

```bash
git clone <this-repo>
cd redmine-knowledge-agent
cp .env.example .env       # 必要な値を編集

task build
task up
```

### アクセス

| サービス  | URL                       |
| --------- | ------------------------- |
| Frontend  | http://localhost:5173     |
| Backend   | http://localhost:8080     |
| Redmine   | http://localhost:3000     |
| Qdrant UI | http://localhost:6333/dashboard |

### Claude Code でタスク実装

リポジトリルートで `claude` を起動し、スラッシュコマンドでタスクを進める:

```
/task T-1-4         # ChunkBuilder の実装
/review             # 直近の変更をレビュー
/arch-check         # ArchUnit でアーキ違反検査
```

詳細は `04-claude-code-guide.md`。

## ディレクトリ構成 (リポジトリ全体)

```
redmine-knowledge-agent/
├── README.md                # ルート README (詳細ドキュメントは docs/ 配下)
├── CLAUDE.md                # Claude Code 用プロジェクトルール
├── .claude/                 # Claude Code 設定 (rules / skills / settings)
├── docs/                    # 本設計ドキュメント群
├── backend/                 # Spring Boot + Koog (オニオン構造)
├── frontend/                # React + Vite + TS
├── infra/docker/            # Dockerfile 群
├── scripts/                 # 運用スクリプト (seed 等)
├── compose.yaml
├── Taskfile.yaml
└── .env.example
```

## 開発フロー

1. `02-tasks.md` から次のタスクを選ぶ
2. Claude Code で `/task <タスクID>` を実行
3. 該当タスクの DoD (テスト含む) を満たす実装が出てきたら受け入れ
4. `task lint && task test` グリーン確認
5. 人間が `git commit` (AI には禁止)

## ライセンス・備考

- 個人/小チーム検証用途を想定 (チケット 〜数千件規模)
- 認証機構は本フェーズでは未実装 (ローカル運用前提)
- Ollama は本リポジトリ管理外。接続先 URL のみ環境変数で指定する
