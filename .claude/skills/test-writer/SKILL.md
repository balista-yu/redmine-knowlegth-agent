---
name: test-writer
description: 既存コードに対して網羅的なテストを追加する Skill。本番コードを変更せず、テストだけを書き足す。Domain/Application は単体テスト + MockK、Infrastructure は Testcontainers で統合テスト、web 層は @WebMvcTest を使う。テスト追加が必要な場面で呼び出す。
allowed-tools: Read, Edit, Write, Grep, Bash(./gradlew test:*), Bash(grep:*), Bash(find:*)
---

# Test Writer

テスト追加専任の Skill。既存の本番コードに対して、規約に従った高品質なテストを追加する。

## 厳守事項

1. **本番コードを変更しない**: テストのために `private → internal` に変更するのも禁止 (どうしても必要なら呼び出し元に相談)
2. **既存テストの assertion を変えない**: 不足していると感じたら新しいテストメソッドを追加する
3. **`.claude/rules/testing.md` の規約に従う**: 命名・配置・ツール選択

## 進め方

### ステップ 1: 対象の理解

1. 対象ファイルを `Read` で確認
2. 既存テストファイル (隣接 `<対象>Test.kt` または `<対象>IT.kt`) を `Read`
3. 対象が触れるレイヤを判定:
   - Domain Model / Domain Service → 単体テスト (Spring 不要)
   - Application Service → 単体テスト + MockK
   - Infrastructure / web → `@WebMvcTest` または `WebTestClient`
   - Infrastructure / external → Testcontainers
   - Infrastructure / persistence → Testcontainers + JPA

### ステップ 2: 不足ケースの特定

対象クラスについて、テストすべきケースを以下の観点で挙げる:

- **正常系**: 主要な入出力パターン
- **境界値**: 0, 1, max, min, 空, 1 文字, 最大文字数
- **null 系**: nullable 引数の null / 非 null
- **異常系**: 想定される例外パス
- **エッジケース**: ドメイン特有 (例: ChunkBuilder なら「description のみ」「全空」「4000 文字」)

`docs/02-tasks.md` の該当タスクの「テスト要件」がある場合はそれを優先。

### ステップ 3: テスト実装

`.claude/rules/testing.md` の規約に従って実装:

- 命名: 日本語バックティック または `methodName_条件_期待結果`
- AAA パターン (空行で区切る)
- 1 テスト 1 検証
- テストデータビルダー (`testdata/`) を活用 (なければ作る)

### ステップ 4: 実行確認

```bash
./gradlew test --tests '*<該当 TestClass>*'
./gradlew test  # 既存も全 pass
```

### ステップ 5: 報告

```markdown
## テスト追加結果

### 対象
<対象クラスのパス>

### 追加テスト
1. `<TestClass>.<testName>`: 何を検証 / どのケース
2. ...

### カバーした観点
- 正常系: ...
- 境界値: ...
- null 系: ...
- 異常系: ...

### カバレッジ補足
- 不足を感じたが追加していない箇所 (理由付き):
  - 例: private 関数のロジック → 公開関数経由で間接的にカバー済み
- 本番コードの変更が必要と感じた箇所 (実装はしない、提案のみ):
  - 例: クラスのコンストラクタが直接 SDK を new しており差し替え不可能 → 依存注入化を提案
```

## やってはいけない

- `Thread.sleep()` でタイミング合わせ (代わりに `runTest` の virtual time)
- 外部ネットワーク依存 (Testcontainers でローカル化)
- `@Disabled` で無効化
- assertion を `assertNotNull` だけで済ます (具体的な値を検証する)
- 1 メソッドで複数の独立 assertion (テストを分ける)
