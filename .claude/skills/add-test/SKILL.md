---
name: add-test
description: 既存コードにテストだけを追加する Skill。/add-test <対象ファイル or クラス名> で呼び出す。本番コードの可視性を変えない (private→internal は要相談)。@Disabled 禁止。
allowed-tools: Read, Edit, Write, Bash(./gradlew test:*), Bash(grep:*), Bash(find:*)
---

# テスト追加

引数で渡された対象クラス・ファイルにテストを追加する。

## 進め方 (この順序を厳守)

### ステップ 1: 現状確認と不足ケース提示

1. 対象を `Read` で確認
2. 既存テストを `Read` (隣接 `<対象>Test.kt` または `<対象>IT.kt`)
3. `docs/02-tasks.md` の該当タスクの「テスト要件」を確認
4. **不足しているテストケースを箇条書きで列挙**
5. 私 (人間) が OK を出すまで次に進まない

### ステップ 2: テスト追加

- 命名規約は `.claude/rules/testing.md` に従う
- 1 テスト 1 検証。AAA パターン
- テストデータビルダーを既存に合わせる (なければ作成して `testdata/` に配置)
- **本番コードの可視性を変えない** (private → internal/public は要相談)

### ステップ 3: 検証

```bash
./gradlew test --tests '*<該当 Test クラス>*'
./gradlew test  # 既存も含めて全 pass
```

### ステップ 4: 完了報告

```markdown
## テスト追加完了

### 追加したテスト
- `<TestClass>.<testName>`: 検証内容
- ...

### カバーした分岐 / エッジケース
- ...

### コミットメッセージ案
`test(<scope>): <summary>`
```

## 制約

- 振る舞いを変える変更は禁止 (本番コードはテストのために最小限のみ変更可、相談必須)
- `@Disabled` は使わない
- 既存テストの assertion を変えない (足りないと感じたら別の test メソッドで追加)
