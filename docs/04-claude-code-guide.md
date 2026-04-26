# Claude Code 利用ガイド (04-claude-code-guide.md)

本プロジェクトは [Claude Code](https://docs.claude.com/en/docs/claude-code/overview) で実装することを前提に設計されている。本ドキュメントはその使い方をまとめる。

## 全体像

Claude Code はリポジトリルートで `claude` コマンドを起動し、対話的に開発を進めるツール。本プロジェクトでは以下の仕組みで Claude Code が **設計書通りに実装する** ように仕込んでいる:

```
セッション開始
  │
  ├── CLAUDE.md (常時ロード) ← プロジェクトの不変ルール
  ├── .claude/rules/architecture.md (常時) ← オニオンの基本
  ├── .claude/rules/code-style.md (常時) ← Clean Code
  └── .claude/settings.json ← 権限 (allow / deny)

ファイル編集時
  └── .claude/rules/<該当>.md (path-scoped で自動ロード)
       例: domain/ を触るとき → domain-layer.md
            application/ → application-layer.md
            infrastructure/ → infrastructure-layer.md
            test/ → testing.md
            frontend/ → frontend.md

Skill 呼び出し時 (`/skill-name` で明示 / description マッチで自動)
  └── .claude/skills/<name>/SKILL.md ← 構造化された実装手順
```

ユーザーは **タスクの内容** だけを伝え、ルールや手順は仕込み済みなので毎回貼り付けない。

## ファイル構成

```
.
├── CLAUDE.md                          # 常時ロード (200 行以内)
└── .claude/
    ├── settings.json                  # 権限 (allow / deny)
    ├── rules/                         # 詳細ルール (path-scoped で自動ロード)
    │   ├── architecture.md            # オニオン全体 (常時)
    │   ├── code-style.md              # Clean Code (常時)
    │   ├── domain-layer.md            # paths: backend/.../domain/**
    │   ├── application-layer.md       # paths: backend/.../application/**
    │   ├── infrastructure-layer.md    # paths: backend/.../infrastructure/**
    │   ├── testing.md                 # paths: **/src/test/**
    │   └── frontend.md                # paths: frontend/**
    └── skills/                        # Skills (`/skill-name` で呼び出し)
        ├── task/SKILL.md              # /task <タスクID>
        ├── review/SKILL.md            # /review
        ├── fix/SKILL.md               # /fix <症状>
        ├── refactor/SKILL.md          # /refactor <パス>
        ├── add-test/SKILL.md          # /add-test <パス>
        ├── arch-check/SKILL.md        # /arch-check
        ├── design-consult/SKILL.md    # /design-consult <内容>
        ├── sync-status/SKILL.md       # /sync-status
        ├── onion-reviewer/SKILL.md    # オニオン違反専用レビュー
        └── test-writer/SKILL.md       # テスト追加専門
```

## Skills 一覧

Skills は `/skill-name` で **明示的に呼び出す** か、SKILL.md の `description` にマッチする状況で **Claude が自動的に呼び出す**。引数を取るものは `/skill-name <引数>` の形で渡す。

### `/task <タスクID>` — メイン実装 Skill

`docs/02-tasks.md` のタスクを 1 件実装する。テスト先行 → 実装 → 検証 → 完了報告の手順で進む。

```
/task T-1-4
```

内部の流れ:
1. AI がタスクの目的・成果物を要約 → ユーザー確認待ち
2. テストコード先行で書く
3. 実装
4. `./gradlew test`, `./gradlew ktlintCheck detekt`, ArchUnit を実行
5. 完了報告 (DoD チェックリスト + コミットメッセージ案)

ユーザーは **タスク ID だけ** 渡せばよい。

### `/review` — コードレビュー

直近の変更をオニオンアーキテクチャ観点で機械的にレビュー。

```
/review
/review backend/src/main/kotlin/.../SyncIssuesApplicationService.kt
```

出力: 重大な問題 / 軽微な指摘 / 良かった点 / 追加すべき ArchUnit ルール提案

### `/fix <症状>` — バグ修正

テスト失敗・不具合を修正。**原因仮説** を提示してからユーザー OK 後に修正に進む安全設計。

```
/fix QdrantTicketChunkRepositoryIT の deleteOrphanChunks テストが落ちる
```

### `/refactor <対象>` — リファクタリング

振る舞いを変えずに構造改善。1 問題 1 リファクタの原則を強制。

```
/refactor application/service/SyncIssuesApplicationService.kt 関数が長い
```

### `/add-test <対象>` — テスト追加

本番コード変更なしでテスト追加。より大きな範囲を任せたいときは `test-writer` Skill を直接呼ぶことも可能。

```
/add-test domain/service/ChunkBuilder.kt
```

### `/arch-check` — ArchUnit 実行

依存方向違反の機械検査。

```
/arch-check
```

### `/design-consult <内容>` — 設計相談

実装前の方針相談。複数案比較 + トレードオフ提示。コードは書かない。

```
/design-consult AnswerQuestionApplicationService が AIAgent 直接依存するのを抽象化すべきか
```

### `/sync-status` — 運用ユーティリティ

backend が起動している状態で同期状態を確認する。

```
/sync-status
```

### `onion-reviewer` — オニオン違反専用レビュー Skill

オニオンアーキテクチャ違反 (依存方向 / レイヤ責務 / 命名) を機械的に検出する専用レビュー。`/review` より深い検査をしたいときや、PR ごとの自動レビューに使える。

呼び出し例: 「`onion-reviewer` で backend/src/main/kotlin/ 全体を監査して」

### `test-writer` — テスト追加専門 Skill

テスト追加専任。本番コード変更なしで、規約準拠のテストを網羅的に追加する。`/add-test` と似ているが、より大きな範囲 (クラス全体、モジュール全体) を任せたいときに使う。

呼び出し例: 「`test-writer` で application/ 配下の全 ApplicationService にテストを追加して」

## 権限設定 (`.claude/settings.json`)

### 自動許可されている操作

- ファイル読み込み (`Read(**)`)
- backend / frontend / infra / scripts / docs の編集
- `./gradlew test/build/ktlintCheck/detekt`
- `npm test/run lint/run dev/run build`
- `task <command>`
- `docker compose <command>`
- `git status / diff / log / branch` (読み取り系)
- `curl`, `ls`, `find`, `grep`, `cat`, `tree`, `wc`

### 拒否されている操作

- `.env` / シークレット系の読み込み
- `rm -rf` / `sudo`

> 注: `git commit` / `git push` 等のコミット系操作は settings.json の deny に明示してはいないが、CLAUDE.md と本ガイドで **AI 実行禁止** とする運用ルールを置いている (人間が手動コミット)。

## 開発フロー (推奨)

### 新規タスクの実装

```bash
# 1. プロジェクトルートで Claude Code 起動
cd /path/to/redmine-knowledge-agent
claude

# 2. タスクを進める
/task T-0-1     # 雛形作成
/task T-0-2     # compose.yaml
/task T-1-1     # backend init + ArchUnit
/task T-1-2     # Domain Model
# ...
```

### 完了後の確認・コミット

```bash
# 3. ローカルでビルド・テスト確認
task lint
task test

# 4. 変更を確認
git diff
git status

# 5. 人間がコミット (AI には任せない運用)
git add .
git commit -m "feat(domain): ChunkBuilder を実装"
git push
```

## トラブルシュート

### Q. AI がルールを守らない

- `CLAUDE.md` が 200 行を大きく超えていないか確認 (アテンション低下)
- 該当 path に対応する `.claude/rules/<layer>.md` が読まれているか確認 (`/memory` コマンドで確認可)
- ルール違反を見つけたら、`CLAUDE.md` に「過去に違反した例」として追記して学習させる

### Q. ArchUnit テストが落ち続ける

`/arch-check` で違反箇所を機械的に特定。修正は `/fix` または `/refactor` で。
**テストを甘くする方向の修正は禁止** (CLAUDE.md / `.claude/rules/architecture.md` で明記)。

### Q. Koog の API が見つからないと言われる

Koog 0.7.3 系は `OllamaEmbeddingModels.NOMIC_EMBED_TEXT` のような名前空間。バージョンが違うと API 名が変わるので、`build.gradle.kts` のバージョンと公式ドキュメントを照合する。

### Q. 権限エラーで止まる

`.claude/settings.json` の `allow` リストに必要なコマンドが含まれているか確認。追加が必要なら settings.json を編集する (人間が直接 or `/update-config` 系 Skill で)。

### Q. `git commit` させたい

AI には `git commit` を任せない運用としている。これは意図的:
- レビュー前の自動コミットを防ぐ
- コミットメッセージの責任を人間が持つ
- 取り返しのつかない操作を AI が単独で行わないようにする

人間が `git diff` で確認後、自分でコミットする運用。

### Q. 個人用設定を入れたい

`CLAUDE.local.md` (`.gitignore` 済み想定) を作成。例:
- ローカルでだけ使う環境変数
- 個人の作業メモ
- 個人的な省略形指示 (「いつも英語で説明して」等)

## カスタマイズ

### ルールを増やす

新しいレイヤルールを追加したい場合:

```bash
# 1. .claude/rules/ に新規 md ファイル作成
# 2. frontmatter で paths を指定
```

```yaml
---
paths:
  - "backend/src/main/kotlin/**/agent/**"
---

# Agent 層ルール
...
```

### Skill を増やす

```bash
# .claude/skills/<name>/SKILL.md を作成
```

```yaml
---
name: <skill-name>
description: <いつ呼ぶか / トリガー条件を具体的に>
allowed-tools: Read, Edit, Bash(...)
---

# <タイトル>

引数で渡された <X> に対して以下を行う。

## 進め方
1. ...
2. ...
```

`/skill-name` で明示呼び出しもできるし、`description` にマッチする状況で Claude が自動判断で呼ぶこともある。専用レビュー人格や専門エージェント的な用途も Skill に統合できる (例: `onion-reviewer`, `test-writer`)。

## 公式ドキュメント参照

- [Claude Code Overview](https://docs.claude.com/en/docs/claude-code/overview)
- [Memory and CLAUDE.md](https://code.claude.com/docs/en/memory)
- [Slash Commands](https://code.claude.com/docs/en/slash-commands)

## まとめ

| やりたいこと            | 使う Skill                |
| ----------------------- | ------------------------- |
| 新しいタスクを実装      | `/task <ID>`              |
| 書かれたコードをレビュー | `/review`                 |
| バグを直す              | `/fix <症状>`             |
| 動くけど汚いコードを直す | `/refactor <パス>`        |
| テストだけ追加          | `/add-test <パス>`        |
| アーキ違反の深い検査    | `onion-reviewer`          |
| テスト網羅追加          | `test-writer`             |
| アーキ違反検査          | `/arch-check`             |
| 設計相談                | `/design-consult <内容>`  |
| 同期状態確認            | `/sync-status`            |

ルールは `.claude/rules/` で **path-scoped に自動ロード** されるので、毎回プロンプトに貼る必要はない。設計書とルールが Claude Code の中に組み込まれているので、ユーザーは **何をしたいか** だけ伝えれば良い。
