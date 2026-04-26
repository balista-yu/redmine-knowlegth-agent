---
paths:
  - "backend/src/test/**"
  - "frontend/src/**/*.test.*"
  - "frontend/src/**/*.spec.*"
  - "frontend/src/__tests__/**"
---

# テストルール (path-scoped)

このルールはテストファイル編集時に自動でロードされる。

## テスト分類と必須レベル

| 層                              | 種別          | 必須 | 使用ツール                              |
| ------------------------------- | ------------- | ---- | --------------------------------------- |
| Domain Service                  | Unit          | ★★★  | JUnit 5 + Kotest assertions              |
| Application Service             | Unit          | ★★★  | JUnit 5 + MockK + Kotest                 |
| Infrastructure / web            | Integration   | ★★   | `@WebMvcTest` / `WebTestClient`          |
| Infrastructure / external/redmine | Integration | ★★   | Testcontainers (`redmine:5` + `mysql:8`) |
| Infrastructure / external/qdrant  | Integration | ★★   | Testcontainers (`qdrant/qdrant`)         |
| Infrastructure / external/ollama  | -          | ☆    | 外部依存のため作らない (録画 or 手動確認) |
| Infrastructure / persistence     | Integration | ★★   | Testcontainers (`postgres:16`)           |
| Architecture                     | Unit (ArchUnit) | ★★★ | ArchUnit                                |
| Frontend                         | Component    | ★★   | Vitest + Testing Library + MSW            |

## 命名規約

- ファイル名:
  - 単体テスト: `<対象クラス名>Test.kt` (例: `ChunkBuilderTest.kt`)
  - 統合テスト: `<対象クラス名>IT.kt` (例: `QdrantTicketChunkRepositoryIT.kt`)
  - ArchUnit: `OnionArchitectureTest.kt`
- メソッド名 (どちらでも可):
  - 日本語バックティック: `` fun `description が空ならチャンク 0 件`() ``
  - English: `methodName_条件_期待結果` (例: `build_descriptionEmpty_returnsEmptyList`)

## テストの基本原則

1. **1 テスト 1 検証**: 1 メソッドに複数の独立した assertion を詰めない
2. **AAA パターン**: Arrange / Act / Assert を明示的に分ける (空行 or コメント)
3. **テスト独立**: 実行順序に依存しない、共有可変状態を持たない
4. **テストデータビルダー**: `backend/src/test/kotlin/.../testdata/` に集約 (例: `IssueFixture.kt`)
5. **可視性のためにコードを変えない**: テストのために `private → internal` に変えない (どうしても必要なら相談)

## ファイル配置

テストは本番コードと同じパッケージ階層に置く:

```
backend/src/main/kotlin/com/example/redmineagent/domain/service/ChunkBuilder.kt
↓
backend/src/test/kotlin/com/example/redmineagent/domain/service/ChunkBuilderTest.kt
```

## Domain / Application 単体テスト

- **Spring を立てない**: `@SpringBootTest` 禁止
- **MockK で interface をモック** (Application のみ)
- **Domain Service は fixture で十分** (純粋関数なのでモック不要なケースが多い)

```kotlin
class ChunkBuilderTest {
    private val chunkBuilder = ChunkBuilder()

    @Test
    fun `description が空ならチャンク 0 件`() {
        val issue = IssueFixture.with(description = "", journals = emptyList())
        val chunks = chunkBuilder.build(issue)
        chunks shouldHaveSize 0
    }
}
```

```kotlin
class SyncIssuesApplicationServiceTest {
    private val redmineGateway = mockk<RedmineGateway>()
    private val ticketChunkRepository = mockk<TicketChunkRepository>()
    // ...

    @Test
    fun `差分なしの場合 chunks_skipped が増える`() = runTest {
        coEvery { redmineGateway.listIssuesUpdatedSince(any(), any(), any()) } returns IssuePage(...)
        // ...
    }
}
```

## Infrastructure 統合テスト (Testcontainers)

- クラス名は `IT` サフィックス
- `@Testcontainers` + `@Container` で実コンテナ起動
- マッピングされたポートを使ってクライアントを構築
- テスト間で状態が残らないように `@BeforeEach` でクリーンアップ

```kotlin
@Testcontainers
class QdrantTicketChunkRepositoryIT {
    companion object {
        @Container
        @JvmStatic
        val qdrant = GenericContainer("qdrant/qdrant:latest")
            .withExposedPorts(6333, 6334)
    }

    private lateinit var repo: QdrantTicketChunkRepository

    @BeforeEach
    fun setUp() = runBlocking {
        repo = QdrantTicketChunkRepository(qdrant.host, qdrant.getMappedPort(6334), "test_collection")
        repo.initCollection(vectorSize = 4)
    }

    @Test
    fun `upsert と search でラウンドトリップできる`() = runTest {
        repo.upsert(listOf(/* ... */))
        val hits = repo.search(floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f), limit = 5)
        hits shouldHaveSize 1
    }
}
```

## Web Controller テスト

```kotlin
@WebMvcTest(SyncController::class)
class SyncControllerTest {
    @Autowired private lateinit var mvc: MockMvc
    @MockkBean private lateinit var syncIssues: SyncIssuesApplicationService

    @Test
    fun `走行中なら 409 を返す`() {
        coEvery { syncIssues.execute(any()) } throws SyncAlreadyRunningException(currentRunId = 41)
        mvc.post("/api/sync") {
            contentType = MediaType.APPLICATION_JSON
        }.andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SYNC_ALREADY_RUNNING") }
        }
    }
}
```

## ArchUnit テスト

- 場所: `backend/src/test/kotlin/com/example/redmineagent/arch/OnionArchitectureTest.kt`
- すべての PR でこのテストが pass することが必須
- 違反検出時、修正は **依存方向を直す**。テストを甘くしない

```kotlin
class OnionArchitectureTest {
    private val classes = ClassFileImporter().importPackages("com.example.redmineagent")

    @Test
    fun `domain は外部フレームワークに依存しない`() {
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "ai.koog..",
                "jakarta.persistence..",
                "io.qdrant..",
                "org.hibernate..",
            ).check(classes)
    }

    @Test
    fun `application は infrastructure に依存しない`() {
        noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .check(classes)
    }
}
```

詳細ルールは `docs/01-design.md` §6.5 と §7.4 参照。

## Frontend テスト

- Vitest + Testing Library
- SSE は MSW でモック
- 主要コンポーネントのみテスト (網羅性より実用性)
- `__tests__` ディレクトリまたは隣接 `.test.tsx` でファイル配置

```tsx
import { render, screen } from "@testing-library/react";
import { describe, it, expect } from "vitest";
import { ChatPage } from "./ChatPage";

describe("ChatPage", () => {
  it("送信すると SSE のチャンクを連結表示する", async () => {
    // MSW で /api/chat をモック
    render(<ChatPage />);
    // ...
  });
});
```

## やってはいけない

- `Thread.sleep()` でタイミング合わせ (`runTest` の virtual time を使う)
- 外部ネットワーク依存 (Testcontainers なら OK、生 HTTPS は NG)
- `@Disabled` で永続的にスキップ (使うなら理由 + 期限コメント必須)
- assertion を緩めて pass させる (テストが落ちたら **本番コードを直す**)
