---
name: task
description: docs/02-tasks.md のタスク分解書から特定のタスク (例 T-1-4) を 1 件実装する Skill。ユーザーが /task <タスクID> で明示的に呼び出す。テスト先行 (テスト → 実装 → 検証 → 完了報告) のフローを厳守する。
allowed-tools: Read, Edit, Write, Bash(./gradlew:*), Bash(npm:*), Bash(task:*), Bash(cat:*), Bash(ls:*), Bash(find:*), Bash(grep:*), Bash(tree:*)
---

# タスク実装

引数で渡された **タスク ID** (例: `T-1-4`) を `docs/02-tasks.md` から探して実装する。

## 必須参照

実装前に以下を必ず読む:

1. `docs/02-tasks.md` の該当タスクセクション
2. `docs/01-design.md` のうち、当該タスクが触れる範囲 (アーキテクチャ §6、データモデル §4、シーケンス §5、テスト戦略 §7、実装方針 §8 のうち関連箇所)
3. `docs/03-api-spec.md` (web 層タスクの場合)

## 進め方 (この順序を厳守)

### ステップ 1: 理解確認

タスクの目的・成果物・テスト要件を **5 行以内** で要約して提示する。私 (人間) が OK を出すまで次に進まない。

要約には以下を含める:
- 何を作るか
- どこに置くか (ディレクトリ・ファイル名)
- 主要な振る舞い
- 触れるレイヤ (Domain / Application / Infrastructure)
- 依存する既存実装

### ステップ 2: テスト先行

`docs/02-tasks.md` の該当タスクの「テスト要件」のすべてのケースを、テストコードとして先に書く。

- 実装側はメソッドシグネチャ + `TODO()` で OK
- テストはコンパイルが通り、assertion 含むスケルトン
- ファイル配置: `.claude/rules/testing.md` の規則に従う

### ステップ 3: 実装

ステップ 2 のテストを通す **最小実装** を行う。

- アーキテクチャルール (`.claude/rules/architecture.md` + 該当層の rule) を厳守
- 命名・スタイルは `.claude/rules/code-style.md` に従う
- ライブラリ追加が必要な場合、`build.gradle.kts` 修正前に **何を追加するか提示して相談**

### ステップ 4: 検証

以下を順に実行し、すべて pass すること:

```bash
# 該当タスクのテスト
./gradlew test --tests '*<該当 Test クラス>*'

# ArchUnit
./gradlew test --tests '*OnionArchitectureTest*'

# 静的解析
./gradlew ktlintCheck detekt
```

### ステップ 5: 完了報告

以下のフォーマットで報告 (CLAUDE.md の「完了報告フォーマット」と同じ):

```markdown
## 完了報告: <タスクID>

### 変更ファイル
- 新規: ...
- 変更: ...

### 追加テスト
- `<TestClass>.<testName>`: 何を検証
- ...

### DoD チェック
- [✓/✗] オニオンアーキテクチャ遵守 (該当層のルール準拠)
- [✓/✗] テストコードあり (要件のすべてのケースを網羅)
- [✓/✗] task lint 通過 (ktlint + detekt)
- [✓/✗] task test 通過 (該当テスト + 既存テスト + ArchUnit)
- [✓/✗] マジック値なし
- [✓/✗] !! 不使用 (使った場合は理由)
- [✓/✗] 命名が意図を表している
- [✓/✗] 関数 30 行以内 (超えた場合は理由)

### ArchUnit 実行結果
(出力サマリ or 「すべて pass」)

### 設計書からの逸脱・独自判断
(あれば。なければ「なし」)

### コミットメッセージ案
`<type>(<scope>): <summary>`
例: `feat(domain): ChunkBuilder を実装し description/journals 分割と長文オーバーラップに対応`
```

## 制約

- 既存ファイルへの不要な変更はしない (1 タスクのスコープを超える書き換えは禁止)
- `git commit` / `git push` は実行しない (人間がレビュー後に手動)
- 不明点は推測せず質問する
