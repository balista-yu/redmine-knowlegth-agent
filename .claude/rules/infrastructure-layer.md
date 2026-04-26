---
paths:
  - "backend/src/main/kotlin/com/example/redmineagent/infrastructure/**"
  - "backend/src/test/kotlin/com/example/redmineagent/infrastructure/**"
---

# Infrastructure 層ルール (path-scoped)

このルールは `infrastructure/` 配下のファイルを編集するときに自動でロードされる。

## 配置と命名

```
infrastructure/
├── web/                # @RestController + DTO + Mapper
├── scheduler/          # @Scheduled
├── persistence/        # JPA Entity / Repository / 実装
│   ├── entity/
│   └── jpa/            # Spring Data interface
├── external/           # 外部 API クライアント
│   ├── redmine/
│   ├── qdrant/
│   └── ollama/
├── agent/              # Koog AIAgent 構築 + Tool
│   └── tool/
└── config/             # @Configuration / @Bean
```

## 命名規約

| 種別                            | 命名パターン                                  | 例                                           |
| ------------------------------- | --------------------------------------------- | -------------------------------------------- |
| Controller                      | `<対象>Controller`                              | `ChatController`, `SyncController`           |
| Domain interface 実装 (永続化)   | `<技術プレフィックス><DomainName>`             | `JpaSyncStateRepository`, `QdrantTicketChunkRepository` |
| Domain interface 実装 (外部 API) | `<技術プレフィックス><DomainName>`             | `RedmineHttpGateway`, `OllamaEmbeddingService`, `KoogChatModel` |
| JPA Entity                       | `<DomainName>Entity`                          | `SyncStateEntity`, `SyncRunEntity`            |
| Spring Data interface            | `<DomainName>JpaRepository`                   | `SyncStateJpaRepository`                      |
| DTO                              | `<DomainName>Dto`                             | `IssueDto`, `JournalDto`                      |
| Mapper                           | `<DomainName>Mapper`                          | `IssueMapper`                                 |

## Domain interface の実装方法

```kotlin
@Component
class JpaSyncStateRepository(
    private val syncStateJpaRepository: SyncStateJpaRepository,  // Spring Data interface
    private val syncRunJpaRepository: SyncRunJpaRepository,
) : SyncStateRepository {                                          // ★ Domain interface 実装

    override suspend fun load(): SyncState {
        val entity = syncStateJpaRepository.findBySource("redmine")
            ?: error("sync_state row not found")
        return entity.toDomain()
    }
    // ...
}
```

ポイント:
- `@Component` (or `@Repository` / `@Service`) を付与
- Domain interface を `: SyncStateRepository` で実装
- 戻り値・引数は Domain Model (Entity → Domain への変換は Mapper か `toDomain()` 拡張関数)

## Mapper の書き方

DTO/Entity ↔ Domain Model の変換は Mapper で集約する。

- 拡張関数で書く (`fun IssueDto.toDomain(): Issue`)
- 双方向必要なら別関数 (`Issue.toDto()`, `IssueDto.toDomain()`)
- 変換ロジックに条件分岐が多い場合は別クラスに切り出す (`IssueMapper`)

## REST Controller のルール

- `@RestController` を付与
- ApplicationService をコンストラクタインジェクション
- Domain Repository / Gateway を **直接呼ばない** (ArchUnit で検出)
- リクエスト validation は Bean Validation (`@Valid`, `@NotBlank`, `@Size`) で
- レスポンスは DTO に変換して返す
- SSE は `WebFlux` の `Flux<ServerSentEvent<*>>` を使用

```kotlin
@RestController
@RequestMapping("/api")
class ChatController(
    private val answerQuestion: AnswerQuestionApplicationService,
) {
    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun chat(@Valid @RequestBody request: ChatRequest): Flow<ServerSentEvent<ChatEventDto>> {
        return answerQuestion.execute(request.message, request.conversationId)
            .map { it.toSse() }
    }
}
```

## Spring `@Scheduled` のルール

- `infrastructure/scheduler/` のクラスにのみ付与
- ApplicationService をコンストラクタ注入
- 重複実行防止: `AtomicBoolean` で走行中フラグ管理 (or ApplicationService 側で `SyncAlreadyRunningException` を catch)
- `@EnableScheduling` は `infrastructure/config/` の `@Configuration` クラスに付与

## JPA Entity のルール

- **Domain Model と別物**: 同じ概念でもクラスは分ける
- フィールドは `@Column` などのアノテーションでマッピング
- `@Entity` クラスは `infrastructure/persistence/entity/` のみに置く
- Domain への変換は `fun toDomain(): SyncState` 拡張関数か Mapper で

```kotlin
@Entity
@Table(name = "sync_state")
class SyncStateEntity(
    @Id @GeneratedValue var id: Long = 0,
    @Column(unique = true) var source: String,
    var lastSyncStartedAt: Instant?,
    // ...
) {
    fun toDomain(): SyncState = SyncState(
        source = source,
        lastSyncStartedAt = lastSyncStartedAt,
        // ...
    )
}
```

## Bean 配線 (`@Configuration`)

- 外部 SDK クライアント (`OllamaClient`, `QdrantClient`) は `infrastructure/config/` で `@Bean` 登録
- 環境変数は `@Value` または `@ConfigurationProperties` で注入
- マジック値はすべて `application.yml` に外出し

```kotlin
@Configuration
class QdrantConfig {
    @Bean
    fun qdrantClient(
        @Value("\${app.qdrant.host}") host: String,
        @Value("\${app.qdrant.port}") port: Int,
    ): QdrantClient = QdrantClient(QdrantGrpcClient.newBuilder(host, port, false).build())
}
```

## エラーハンドリング

- 一時エラー (5xx, タイムアウト): exponential backoff で 3 回リトライ
- リトライしても失敗 → Domain or Application の例外に変換して投げる
- SDK 例外 (例: `QdrantException`) を生のまま Application に流さない

## テスト

- Web Controller: `@WebMvcTest` または `WebTestClient` で ApplicationService をモック
- 外部システム連携: Testcontainers で実コンテナ起動
- 詳細は `.claude/rules/testing.md` 参照

## よくある違反例

| 違反パターン                                            | 修正方針                                                       |
| ------------------------------------------------------- | -------------------------------------------------------------- |
| Controller が直接 Repository を呼ぶ                      | ApplicationService 経由にする (ArchUnit で検出)                  |
| Entity を Controller の戻り値にする                      | DTO に変換してから返す                                           |
| `@Entity` クラスを Domain として再利用                    | Domain 用クラスを別途作り、Mapper で変換                          |
| マジック値 (`"redmine"`, `768`, `100`) を直接コードに埋め込む | `application.yml` に外出しして `@Value` 注入、または定数クラスに集約 |
