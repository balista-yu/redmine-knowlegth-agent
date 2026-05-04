---
paths:
  - "backend/src/main/kotlin/com/example/redmineagent/application/**"
  - "backend/src/test/kotlin/com/example/redmineagent/application/**"
---

# Application 層ルール (path-scoped)

このルールは `application/` 配下のファイルを編集するときに自動でロードされる。

## 絶対遵守

1. **依存できるのは Domain 層のみ** (`infrastructure.*` を import 禁止)
2. **使ってよい Spring**:
   - `@Service` / `@Component` (DI 配線のため、クラスへの付与のみ)
3. **使ってはいけない Spring**:
   - `@RestController`, `@Controller` (Infrastructure の責務)
   - `@Entity`, `@Table` 等 JPA (Infrastructure の責務)
   - `@Transactional` (トランザクション境界は Infrastructure 層で切る)
4. **AIAgent (Koog) は例外的に直接利用してよい**: `AnswerQuestionApplicationService` は `AIAgent` インスタンスを注入される。これは設計書 §6.4 で明記された妥協点

## ファイル配置

```
application/
├── service/       # ApplicationService (ユースケース)
└── exception/     # ユースケース固有の例外
```

## ApplicationService の責務

- **オーケストレーション**: Domain interface を組み合わせてユースケースを実現
- **トランザクション境界の指示** (実際のアノテーションは Infrastructure 側で付与)
- **エラーハンドリングと例外変換**: Infrastructure からの例外をユースケース固有の例外に変換 (例: `EmbeddingTooLongException`)

ApplicationService は **ロジックを持つ** が、それは「ドメインロジック」ではなく「ユースケースのフロー」。
Domain Service とごっちゃにしない:

| 区分                  | 例                                                             |
| --------------------- | -------------------------------------------------------------- |
| Domain Service の責務 | `ChunkBuilder.build(issue)` (issue → chunks の純粋変換)         |
| Application の責務    | 「Redmine から fetch → ChunkBuilder で変換 → embed → Qdrant に upsert」 のフロー |

## 命名規約

- クラス名: `<動詞><目的語>ApplicationService`
  - ✓ `SyncIssuesApplicationService`, `AnswerQuestionApplicationService`, `ReconcileApplicationService`
  - ✗ `SyncService`, `IssueService`
- メソッド名: `execute(...)` を主要メソッド名にする (1 ApplicationService = 1 ユースケース)

## コンストラクタインジェクション

```kotlin
@Service
class SyncIssuesApplicationService(
    private val redmineGateway: RedmineGateway,
    private val ticketChunkRepository: TicketChunkRepository,
    private val embeddingService: EmbeddingService,
    private val syncStateRepository: SyncStateRepository,
    private val chunkBuilder: ChunkBuilder,
) {
    suspend fun execute(mode: SyncMode = SyncMode.INCREMENTAL): SyncRunSummary {
        // ...
    }
}
```

- `private val` でフィールド宣言
- 引数の interface は **すべて Domain で定義されたもの** のみ
- Infrastructure の具象実装を直接受けない (Spring が解決する)

## 例外設計

- ユースケース固有の例外は `application/exception/` に置く
- 例: `SyncAlreadyRunningException`, `EmbeddingTooLongException`,
  `OllamaUnavailableException`, `QdrantUnavailableException`
- Domain で定義された例外は Domain 層に置く
- Infrastructure の SDK 例外をそのまま投げ上げない (Application 内で必ず変換)

### catch 粒度ポリシー (統一規則)

ApplicationService 内で「想定 SDK 例外を Error イベント等に変換」する場面では:

1. **catch する型は `RuntimeException`** (`Exception` ではない)
   - Kotlin に checked exception は無い
   - `Exception` を catch すると `CancellationException` (coroutine 中断) まで吸ってしまう
2. **type-based dispatch を優先**: 例外の型で `is` 判定して分岐
   - メッセージ文字列の substring match は SDK バージョン変更で破断するため避ける
   - 例外型が足りなければ Infrastructure 層で **専用の Application 例外** に変換してから上げる
3. **`@Suppress("TooGenericExceptionCaught")` は必要なときだけ + コメントで理由明記**

```kotlin
// ✓ OK
try {
    val response = agent.run(message)
    ...
} catch (
    @Suppress("TooGenericExceptionCaught") e: RuntimeException,
) {
    emit(toErrorEvent(e))
}

private fun toErrorEvent(e: RuntimeException): AgentEvent.Error =
    when (e) {
        is OllamaUnavailableException -> AgentEvent.Error("OLLAMA_UNAVAILABLE", e.message ?: "")
        is QdrantUnavailableException -> AgentEvent.Error("QDRANT_UNAVAILABLE", e.message ?: "")
        else -> AgentEvent.Error("INTERNAL", e.message ?: e::class.java.simpleName)
    }

// ✗ NG (Exception で受けて message substring で判定)
try { ... } catch (e: Exception) {
    if ("ollama" in e.message.orEmpty()) ...   // SDK 変更で簡単に破断
}
```

## テストの書き方

- 単体テスト + MockK で全 interface をモック
- Spring を立てない (`@SpringBootTest` 禁止)
- 正常系 + 主要異常系を網羅

```kotlin
@Test
fun `差分なしの場合 chunks_skipped が増える`() = runTest {
    val redmineGateway = mockk<RedmineGateway>()
    val repo = mockk<TicketChunkRepository>()
    // ... 詳細は .claude/rules/testing.md
}
```

詳細は `.claude/rules/testing.md` 参照。

## よくある違反例

| 違反パターン                                                   | 修正方針                                                    |
| -------------------------------------------------------------- | ----------------------------------------------------------- |
| `infrastructure.external.qdrant.QdrantTicketChunkRepository` を直接 import | Domain interface (`TicketChunkRepository`) を経由する           |
| `WebClient` を引数に取る                                        | `RedmineGateway` (Domain interface) を引数に取る              |
| `@Transactional` を ApplicationService に付ける                 | Infrastructure 層の Repository 実装側で境界を切る              |
| 例外を try-catch 無しで Spring の `WebClientResponseException` のまま投げる | Domain or Application の例外に変換して投げる                    |
| 1 つの ApplicationService に 2 つ以上のユースケースを詰める     | 別クラスに分ける (`Sync~ApplicationService` と `Reconcile~`) |
