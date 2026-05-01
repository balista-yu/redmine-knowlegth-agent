---
paths:
  - "backend/src/test/**"
  - "frontend/src/**/*.test.*"
  - "frontend/src/**/*.spec.*"
  - "frontend/src/__tests__/**"
---

# テストルール (path-scoped)

このルールはテストファイル編集時に自動でロードされる。

## テストフレームワーク

- **既定は Kotest 6 (`FunSpec`)**: 単体テストはすべて `class XxxTest : FunSpec({ ... })` で書く
  - assertion: `io.kotest.matchers.*` (`shouldBe` / `shouldThrow` / `shouldHaveSize` 等)
  - mocking: MockK (`io.mockk.*`) と組合せ可
  - `IsolationMode.InstancePerTest` を `src/test/kotlin/io/kotest/provided/ProjectConfig.kt`
    で有効化済み (テストごとに spec インスタンス再生成 = JUnit 同等)
- **Spring を立てるテストのみ JUnit 5 を継続**: `@SpringBootTest` / `@WebMvcTest` 等は
  Kotest 6 用の Spring extension が未公開のため、引き続き JUnit `@Test` で記述する

## テスト分類と必須レベル

| 層                              | 種別          | 必須 | 使用ツール                              |
| ------------------------------- | ------------- | ---- | --------------------------------------- |
| Domain Service                  | Unit          | ★★★  | Kotest 6 (FunSpec)                       |
| Application Service             | Unit          | ★★★  | Kotest 6 (FunSpec) + MockK               |
| Infrastructure / web            | Integration   | ★★   | JUnit 5 + `@WebMvcTest` / `WebTestClient` |
| Infrastructure / external/redmine | Integration | ★★   | Kotest 6 + Testcontainers (`redmine:5` + `mysql:8`) |
| Infrastructure / external/qdrant  | Integration | ★★   | Kotest 6 + Testcontainers (`qdrant/qdrant`) |
| Infrastructure / external/ollama  | -          | ☆    | 外部依存のため作らない (録画 or 手動確認) |
| Infrastructure / persistence     | Integration | ★★   | JUnit 5 + Testcontainers (`postgres:16`) |
| Architecture                     | Unit (ArchUnit) | ★★★ | Kotest 6 + ArchUnit                     |
| Frontend                         | Component    | ★★   | Vitest + Testing Library + MSW            |

## 命名規約

- ファイル名:
  - 単体テスト: `<対象クラス名>Test.kt` (例: `ChunkBuilderTest.kt`)
  - 統合テスト: `<対象クラス名>IT.kt` (例: `QdrantTicketChunkRepositoryIT.kt`)
  - ArchUnit: `OnionArchitectureTest.kt`
- テスト名 (FunSpec の `test("...")` 内):
  - 日本語自由文: `test("description が空ならチャンク 0 件") { ... }`
  - English: `test("build_descriptionEmpty_returnsEmptyList") { ... }`
- JUnit 5 を残す Spring テストのみ従来の関数名 + バックティック:
  - `` fun `走行中なら 409 を返す`() ``

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
class ChunkBuilderTest : FunSpec({
    test("description が空ならチャンク 0 件") {
        val issue = IssueFixture.with(description = "", journals = emptyList())
        val chunks = ChunkBuilder.build(issue)
        chunks shouldHaveSize 0
    }
})
```

```kotlin
class SyncIssuesApplicationServiceTest : FunSpec({
    val redmineGateway = mockk<RedmineGateway>()
    val ticketChunkRepository = mockk<TicketChunkRepository>()
    // ... (InstancePerTest なのでテストごとに spec が再生成され、mockk もフレッシュ)

    test("差分なしの場合 chunks_skipped が増える") {
        coEvery { redmineGateway.listIssuesUpdatedSince(any(), any(), any()) } returns IssuePage(...)
        // ... suspend 関数を直接呼べる (FunSpec の test{} はコルーチンスコープ)
    }
})
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
