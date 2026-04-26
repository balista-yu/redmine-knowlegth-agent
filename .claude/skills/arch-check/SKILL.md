---
name: arch-check
description: ArchUnit テストでオニオンアーキテクチャの依存方向違反を検査する Skill。/arch-check で呼び出す。違反検出時は本番コードを直す方針 (テストを甘くしない) で対応する。
allowed-tools: Bash(./gradlew test:*), Read, Bash(grep:*), Bash(find:*)
---

# アーキテクチャ検査

ArchUnit テストを実行し、オニオンアーキテクチャの依存方向違反を検出する。

## 実行

```bash
./gradlew test --tests '*OnionArchitectureTest*'
```

## 違反があった場合の対応

1. ArchUnit のエラー出力から **どのファイル** が **どのパッケージ** に違反依存しているかを特定
2. 設計書 §6 / `.claude/rules/architecture.md` に照らして、どう直すべきかを判断:
   - Domain → Spring の場合 → import を消し、interface 経由にする
   - Application → Infrastructure の場合 → Domain interface を経由する
   - web → Repository 直叩き → ApplicationService を経由する
3. **テストを甘くする方向の修正は禁止**。本番コードを直す
4. 修正後に再度 ArchUnit 実行

## 出力形式

```markdown
## ArchUnit 検査結果

### サマリ
- 実行ルール数: N
- 違反: 0 件 / M 件

### 違反詳細 (あれば)
1. ルール: `domain は外部フレームワークに依存しない`
   - 違反箇所: `domain/model/Issue.kt:5`
   - 違反内容: `org.springframework.format.annotation.DateTimeFormat` を import
   - 修正方針: `@DateTimeFormat` を削除し、Infrastructure 層の DTO 側でフォーマット指定

### ルール別 pass/fail
- domain は外部フレームワークに依存しない: ✓
- application は infrastructure に依存しない: ✓
- web は repository を直接呼ばない: ✗ (1 件)
- ...
```
