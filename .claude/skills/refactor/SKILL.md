---
name: refactor
description: 振る舞いを変えずに構造を改善する Skill。/refactor <対象パス> [目的] で呼び出す。既存テスト全 pass 維持を厳守。新規依存追加は事前相談。1 回 1 問題のスコープ。
allowed-tools: Read, Edit, Write, Bash(./gradlew test:*), Bash(./gradlew ktlintCheck:*), Bash(./gradlew detekt:*), Bash(grep:*), Bash(find:*), Bash(wc:*)
---

# リファクタリング

引数で渡された対象 (パス + 任意で目的) をリファクタリングする。

## 厳守

1. **振る舞いは変更しない**: 既存テストはすべて pass のまま
2. **新規依存ライブラリの追加禁止** (相談してから)
3. **1 つの問題に 1 度のリファクタリング**: 複数の問題を混ぜない

## 進め方 (この順序を厳守)

### ステップ 1: 現状分析と方針提示

1. 対象を `Read` で確認
2. 何が問題か (関数長すぎ・命名・マジック値・責務混在 等) を特定
3. **リファクタリング方針を 5 行以内で提示**
4. 私 (人間) が OK を出すまで次に進まない

### ステップ 2: 実施

- 既存テストを先に確認 (テストカバレッジが薄い場合、まずテストを補強してから)
- 振る舞いを変えない範囲で構造を変える
- アーキテクチャルール・コードスタイルルールを満たす方向に動かす

### ステップ 3: 検証

```bash
./gradlew test                           # 全テスト pass
./gradlew test --tests '*OnionArchitectureTest*'
./gradlew ktlintCheck detekt
```

### ステップ 4: 完了報告

```markdown
## リファクタリング完了

### Before / After
- 関数行数: 80 → 28 (3 関数に分割)
- マジック値: 5 → 0 (定数化)
- ...

### 変更ファイル
- ...

### 既存テストへの影響
すべて pass (本数: N)

### コミットメッセージ案
`refactor(<scope>): <summary>`
```

## よくあるリファクタ目的とアプローチ

| 問題                          | アプローチ                                                  |
| ----------------------------- | ----------------------------------------------------------- |
| 関数が長い (>30 行)            | private 関数 / 拡張関数に分割。早期 return で平坦化          |
| マジック値                     | `companion object` に定数化、または `application.yml` に外出し |
| 命名が意図を表していない       | 動詞 + 目的語に変更、Boolean は `is/has/can`                 |
| 例外ハンドリングが冗長         | catch を 1 か所に集約、Domain or Application の例外に変換    |
| テストしづらい                 | 依存を interface に切り出し、Domain ポート化                  |
| レイヤ違反 (Application が SDK 直接) | interface 経由に置き換え                                  |
