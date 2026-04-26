---
name: fix
description: テスト失敗・バグを修正する Skill。/fix <症状の説明 or 失敗テスト名> で呼び出す。再現 → 原因仮説 → 最小修正 → 検証 → 報告のフローを厳守。テストの assertion を緩めて pass させる対応は禁止。
allowed-tools: Read, Edit, Write, Bash(./gradlew test:*), Bash(./gradlew ktlintCheck:*), Bash(./gradlew detekt:*), Bash(npm test:*), Bash(grep:*), Bash(find:*), Bash(git diff:*), Bash(git log:*)
---

# バグ/失敗の修正

引数で渡された症状 (テスト失敗名 or バグの説明) を修正する。

## 進め方 (この順序を厳守)

### ステップ 1: 再現と原因仮説

1. `./gradlew test` などでまず再現
2. 失敗箇所のコードと関連するファイル (テストコード含む) を `Read` で確認
3. **原因仮説を 3 行以内で報告**。この時点で修正は始めない
4. 私 (人間) が OK を出すまで次に進まない

### ステップ 2: 修正

- **最小限の修正**: 関連しないリファクタリングは混ぜない
- 設計上の問題に起因する場合、修正前に **設計の見直しを提案** する
- バグの **本質的な原因** に対処する。テストの assertion を緩めて pass させるのは不可
- Domain interface のシグネチャを変える場合、影響範囲 (実装と呼び出し元) をすべて挙げてから着手

### ステップ 3: 検証

```bash
./gradlew test                           # 全テスト
./gradlew test --tests '*OnionArchitectureTest*'  # ArchUnit
./gradlew ktlintCheck detekt             # 静的解析
```

### ステップ 4: 完了報告

```markdown
## 修正完了

### 原因
<バグの根本原因>

### 修正内容
<何をどう変えたか、なぜそれで直るか>

### 影響範囲
<他に影響する箇所と、それぞれへの対処>

### 追加・修正したテスト
<回帰防止のためのテスト>

### コミットメッセージ案
`fix(<scope>): <summary>`
```

## 制約

- 設計上の問題が原因なら、コードを直す前に提案を出す
- テストを甘くして pass させる対応は不可
- 1 件のバグに対して 1 回の修正。複数を混ぜない
