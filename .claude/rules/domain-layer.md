---
paths:
  - "backend/src/main/kotlin/com/example/redmineagent/domain/**"
  - "backend/src/test/kotlin/com/example/redmineagent/domain/**"
---

# Domain 層ルール (path-scoped)

このルールは `domain/` 配下のファイルを編集するときに自動でロードされる。

## 絶対遵守

1. **import で外部フレームワークを禁止**: 以下を一切 import しない
   - `org.springframework.*`
   - `ai.koog.*`
   - `jakarta.persistence.*` / `javax.persistence.*`
   - `org.hibernate.*`
   - `io.qdrant.*`
   - `io.netty.*`, `io.ktor.*`
   - その他 SDK / フレームワーク
2. **使用可能な依存**: `kotlin.*`, `kotlinx.*` のみ
3. **アノテーション禁止**: `@Component`, `@Service`, `@Entity`, `@Repository`, `@Configuration`, `@Bean` などすべて禁止 (POJO/POKO で書く)

## ファイル配置

```
domain/
├── model/         # Entity / Value Object
├── repository/    # 永続化されたコレクションへのアクセス interface
├── gateway/       # 外部システムへの問い合わせ / 機能的サービスの interface
└── service/       # 純粋ドメインロジック (副作用なし)
```

## Domain Model のルール

- **immutable**: `data class` + `val` プロパティ (mutable プロパティは原則不可)
- **値オブジェクト的**: 等価性は値ベース (`data class` の自動 equals でよい)
- **ビジネスルールの不変条件は `init` ブロックで `require()` する**

```kotlin
data class TicketChunk(
    val ticketId: Int,
    val chunkType: ChunkType,
    val chunkIndex: Int,
    val content: String,
    val contentHash: String,
) {
    init {
        require(ticketId > 0) { "ticketId must be positive" }
        require(content.isNotBlank()) { "content must not be blank" }
    }
}
```

## interface (Repository / Gateway / Service) のルール

- 引数・戻り値は **すべて Domain Model のみ** で構成する
- SDK 型 (例: Qdrant の `Points`) を露出させない
- `suspend fun` を使用してよい (kotlinx.coroutines.* は許可)
- 例外は Domain で定義した例外クラス、または標準例外のみ投げる

```kotlin
// ✓ OK
interface RedmineGateway {
    suspend fun listIssuesUpdatedSince(since: Instant?, offset: Int, limit: Int): IssuePage
}

// ✗ NG (Spring の WebClient レスポンスを返すなど)
interface RedmineGateway {
    suspend fun listIssues(): Mono<List<IssueDto>>  // 違反
}
```

## Domain Service のルール

- **純粋関数**: I/O なし、副作用なし、外部依存なし
- 状態を持たない (フィールドを持たないか、すべて `val`)
- Spring DI を期待しない (Application Service から `new` されるか、Infrastructure の `@Bean` で登録)

```kotlin
class ChunkBuilder(
    private val hashCalculator: HashCalculator = HashCalculator(),
) {
    fun build(issue: Issue): List<TicketChunk> {
        // 純粋ロジックのみ
    }
}
```

## テストの書き方

- 単体テスト必須。Spring を立てない (`@SpringBootTest` 禁止)
- MockK 不要 (Domain Service は依存少なく純粋なので fixture で十分なケースが多い)
- テストデータビルダーは `backend/src/test/kotlin/.../testdata/` に集約

詳細は `.claude/rules/testing.md` 参照。

## よくある違反例

| 違反パターン                                              | 修正方針                                                  |
| --------------------------------------------------------- | --------------------------------------------------------- |
| Domain クラスに `@Component` を付ける                      | アノテーション削除。Spring 配線は Infrastructure 層の `@Configuration` で `@Bean` 登録 |
| Repository interface の戻り値を Spring `Mono`/`Flux` にする | `Flow` または `suspend fun` を使う                          |
| Domain クラスで JPA `@Entity` を兼用する                   | Domain クラスと別に `infrastructure/persistence/entity/` に Entity を作り、Mapper で変換 |
| Domain Service に外部 API 呼び出しを書く                    | API 呼び出しは Application Service から interface 経由で。Domain Service には純粋ロジックのみ残す |
