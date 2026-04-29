# Redmine Knowledge Agent

Redmine のチケットを RAG ナレッジ源とする AI エージェント (Kotlin / Spring Boot / Koog + React + Qdrant + Postgres + Ollama)。

## このファイルの役割

Claude Code が **毎セッションで必ず読む** プロジェクトの不変ルール。詳細は path-scoped で `.claude/rules/` に分割している。

## 厳守事項

1. **オニオンアーキテクチャ遵守**: Domain → Application → Infrastructure の 4 層、依存方向は外→内のみ
   - Domain 層から Spring / Koog / JPA / Qdrant SDK 等を import 禁止
   - Application 層は Domain にのみ依存。Infrastructure に依存しない
   - 外部システムは Domain で interface 定義、Infrastructure で実装 (依存性逆転)
   - ArchUnit テストで機械検査される。違反は CI で落ちる
2. **テストコード必須**: 該当層に応じたテスト (詳細は `.claude/rules/testing.md`) なしの実装は不完全とみなす
3. **テスト先行**: 新規実装は「テスト書く → 実装」の順。実装鏡写しテストを後付けで書くのは禁止
4. **`task lint && task test` がグリーン**: コミット前に必ず両方確認
5. **Ollama は本リポジトリ管理外**: `OLLAMA_BASE_URL` で接続先を指定。Dockerfile / Compose 定義を追加しない

## 禁止事項

- ドキュメント (`docs/01-design.md`, `docs/02-tasks.md`) を読まずに実装を始めること
- テストを後回しにしてコミットすること
- ファイルを大量に書き換えてから差分を見せること (1 タスク = 1〜数コミット粒度)
- 設計書の方針を独断で変更すること (変更したい場合は提案として相談)
- Domain 層に Spring / Koog / JPA 等のアノテーションや型を持ち込むこと
- `!!` 演算子の使用 (やむを得ない場合は理由をコメントで明記)
- マジック値 (リテラル数値・文字列) を直接コードに埋め込むこと

## 不明点があった場合

推測で進めず、必ず質問する。

## ディレクトリ早見表

```
.
├── CLAUDE.md                    # 本ファイル (常に読まれる)
├── .claude/
│   ├── rules/*.md               # path-scoped 詳細ルール (該当ファイル作業時のみロード)
│   ├── skills/<name>/SKILL.md   # Skills (`/skill-name` で呼び出し / Claude が自動判断で起動)
│   └── settings.json            # 権限設定
├── docs/
│   ├── README.md                # プロジェクト概要・クイックスタート
│   ├── 01-design.md             # 設計書 (アーキテクチャ・データモデル・シーケンス)
│   ├── 02-tasks.md              # タスク分解書 (実装担当はここから次タスクを選ぶ)
│   ├── 03-api-spec.md           # REST API 仕様
│   └── 04-claude-code-guide.md  # Claude Code 利用ガイド (Skills 一覧)
├── backend/                     # Spring Boot + Koog (オニオン構造)
├── frontend/                    # React + Vite + TS
├── infra/docker/                # Dockerfile 群
└── compose.yaml, Taskfile.yaml
```

backend のレイヤ構造は `docs/01-design.md` §6.4 を参照。

## 主要コマンド

```bash
task              # コマンド一覧
task up           # 全コンテナ起動 (frontend, backend, redmine, qdrant, postgres)
task down         # 停止
task logs-app     # backend ログ
task test         # backend + frontend の全テスト
task test-be      # backend のみ (unit + integration)
task lint         # Spotless (ktlint) + Detekt + ESLint
task sync         # 手動で差分同期実行
task reconcile    # 手動で Reconciliation
task seed-redmine # Redmine にサンプルチケット投入
```

## 開発フロー (推奨)

1. `docs/02-tasks.md` から次のタスクを選ぶ
2. Skill `/task <タスクID>` で実装を依頼 (例: `/task T-1-4`)
3. テスト先行 → 実装 → `task lint && task test` グリーン確認
4. 完了報告のフォーマットに従って成果を提示
5. AI が Conventional Commits 形式でコミットし、作業ブランチへ push (`main` への直接 push は禁止。レビューは PR 作成後に人間が実施)

## 現在のフェーズ

詳細は `docs/02-tasks.md` を参照。Phase 0 (インフラ) → Phase 1 (バッチ取込) → Phase 2 (チャット API) → Phase 3 (フロント) → Phase 4 (拡張) の順で進める。

## Skills 早見

`.claude/skills/<name>/SKILL.md` に定義。`/skill-name` で明示的に呼び出すか、description にマッチする状況で Claude が自動的に呼び出す。

| Skill                | 用途                                          |
| -------------------- | --------------------------------------------- |
| `/task <ID>`         | タスク分解書のタスクを実装する                 |
| `/review`            | 直近の変更をオニオン観点でレビュー              |
| `/fix <症状>`        | 失敗テスト・バグ修正                           |
| `/refactor <パス>`   | リファクタリング                               |
| `/add-test <パス>`   | テストだけ追加                                 |
| `/arch-check`        | ArchUnit テスト実行 + 違反箇所の説明           |
| `/design-consult`    | 実装前の設計判断相談                            |
| `/sync-status`       | 同期状態を確認                                 |
| `onion-reviewer`     | オニオンアーキテクチャ違反の機械的検査 (専用レビュー) |
| `test-writer`        | テスト追加専門 (本番コード変更なし)            |

詳細は `docs/04-claude-code-guide.md` 参照。

## 完了報告フォーマット (毎回守る)

実装タスク完了時は以下を必ず提示:

1. 変更したファイル一覧
2. 新規追加したテストケース数とそれぞれの目的
3. DoD チェックリスト (`.claude/rules/` の該当ルールを 1 項目ずつ ✓/✗ で)
4. ArchUnit テスト実行結果
5. 設計書からの逸脱や独自判断 (あれば理由付きで)
6. Conventional Commits 形式のコミットメッセージ案 (例: `feat(domain): ChunkBuilder を実装`)
