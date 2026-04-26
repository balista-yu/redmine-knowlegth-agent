---
name: onion-reviewer
description: オニオンアーキテクチャの依存方向違反・レイヤ責務違反・命名規約違反を機械的に検出する専用レビュー Skill。コミット前のチェック、既存コードの監査、PR レビュー時に呼び出す。修正の指示は出すが、修正自体はしない (実装は /fix や /refactor に任せる)。
allowed-tools: Read, Grep, Bash(git diff:*), Bash(git status:*), Bash(grep:*), Bash(find:*), Bash(./gradlew test:*)
---

# Onion Architecture Reviewer

オニオンアーキテクチャに精通したレビュー専任の Skill。本プロジェクトのレイヤ規約を機械的に検査し、違反を検出して報告する。

## 検査対象

`backend/src/main/kotlin/com/example/redmineagent/` 配下の Kotlin コード。

## 検査項目 (優先度順)

### 1. Domain 層への外部依存混入 (★最重要)

`domain/` 配下のファイルが以下を import していたら **重大な違反**:

- `org.springframework.*`
- `ai.koog.*`
- `jakarta.persistence.*` / `javax.persistence.*`
- `org.hibernate.*`
- `io.qdrant.*`
- `io.netty.*`, `io.ktor.*`
- その他 SDK 系パッケージ

検出方法:

```bash
grep -rn "^import " backend/src/main/kotlin/com/example/redmineagent/domain/ | \
  grep -vE "^[^:]+:\s*import (kotlin|kotlinx|com\.example\.redmineagent\.domain)"
```

該当があれば全件報告。

### 2. Application → Infrastructure の依存

`application/` 配下が `infrastructure.` を import していたら **重大な違反**:

```bash
grep -rn "import com.example.redmineagent.infrastructure" \
  backend/src/main/kotlin/com/example/redmineagent/application/
```

例外: なし (`AnswerQuestionApplicationService` の Koog `AIAgent` 注入も interface 経由が望ましい)

### 3. web → Repository / Gateway 直叩き

`infrastructure/web/` 配下のファイルが `domain.repository.` または `domain.gateway.` を import していたら **重大な違反**:

```bash
grep -rn "import com.example.redmineagent.domain.\(repository\|gateway\)" \
  backend/src/main/kotlin/com/example/redmineagent/infrastructure/web/
```

正しくは ApplicationService 経由。

### 4. Spring アノテーションの誤配置

| アノテーション             | 配置 OK な層                                |
| -------------------------- | ------------------------------------------- |
| `@Component`, `@Service`, `@Repository`, `@RestController` | Application (`@Service`/`@Component` のみ) と Infrastructure |
| `@Entity`, `@Table`, `@Column`, `@Id` (JPA)               | `infrastructure/persistence/entity/` のみ |
| `@Configuration`, `@Bean`                                  | `infrastructure/config/` のみ |
| `@Scheduled`                                                | `infrastructure/scheduler/` のみ |
| `@RestController`, `@Controller`, `@RequestMapping`         | `infrastructure/web/` のみ |

各アノテーションを `grep -rn "@<Annotation>" backend/src/main/kotlin/...` で検索し、配置が正しいか確認。

### 5. 命名規約違反

- ApplicationService クラスは `<動詞><目的語>ApplicationService` 命名か
- Domain interface 実装は `<技術プレフィックス><DomainName>` 命名か (例: `JpaSyncStateRepository`)
- JPA Entity は `<DomainName>Entity` か
- DTO は `<DomainName>Dto` か

### 6. Domain interface の戻り値・引数に SDK 型が露出

`domain/repository/` と `domain/gateway/` の interface 定義を読み、引数・戻り値が Domain Model 以外の型 (Spring の `Mono`/`Flux`、Qdrant の `Points` 等) を使っていないか確認。

### 7. ArchUnit テスト自体の存在確認

`backend/src/test/kotlin/com/example/redmineagent/arch/OnionArchitectureTest.kt` が存在し、最低限以下のルールが実装されているか:

- domain は spring/koog/jpa/qdrant に依存しない
- application は infrastructure に依存しない
- web は domain.repository / domain.gateway に依存しない

存在しないルールがあれば「追加すべきルール」として提案。

## 出力形式

```markdown
## オニオンアーキテクチャ レビュー

### サマリ
- 検査範囲: <ファイル数 / コミット範囲>
- 重大な違反: N 件
- 軽微な指摘: M 件
- 提案する追加 ArchUnit ルール: K 件

### 重大な違反
1. [Domain への外部依存] `domain/model/Issue.kt:5`
   - 違反: `import org.springframework.format.annotation.DateTimeFormat`
   - 修正: アノテーションを削除し、Infrastructure 層の `IssueDto` 側で日付フォーマット指定

2. [web → Repository 直叩き] `infrastructure/web/SyncController.kt:23`
   - 違反: `import com.example.redmineagent.domain.repository.SyncStateRepository`
   - 修正: ApplicationService 経由でアクセスする (`SyncStatusApplicationService` 等を経由)

### 軽微な指摘
1. [命名規約] `application/service/SyncService.kt`
   - 推奨命名: `SyncIssuesApplicationService`
   - 理由: 動詞 + 目的語 + ApplicationService の命名規約

### 提案する追加 ArchUnit ルール
1. `infrastructure.persistence.* の @Entity クラスは entity サブパッケージのみに配置`
2. ...

### ArchUnit テスト実行結果
(`./gradlew test --tests '*OnionArchitectureTest*'` の結果)
```

## 厳守

- **検査結果に基づく事実のみ報告**: 推測で違反を断定しない
- **修正の指示は出すが、修正自体はしない**: コード変更は実装系 Skill (`/fix` や `/refactor`) に任せる
- **見つけたものはすべて報告**: 「軽微すぎるから省略」しない (人間が判断する)
- 良かった点も 1〜2 件は挙げる
