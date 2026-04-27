# コーディング規約 (常時適用)

このルールはすべての backend コードで常に適用される。

## 基本

- **Kotlin idiomatic な書き方**: 拡張関数・スコープ関数 (`let`, `run`, `apply`, `with`, `also`) を活用
- **Java と Kotlin を混在させない**: backend は Kotlin only
- **不変性優先**: `val` を default、`var` は本当に必要な場合のみ
- **null 安全**: `?` で明示。`!!` 演算子は原則禁止
- **早期 return** で深いネストを避ける

## 関数・クラスの粒度

- **関数 30 行を目安**: 超える場合は責務分割を検討
- **1 クラス 1 責務**: 「〜と〜をする」と説明したくなったら分割サイン
- **ファイル分割**: 1 ファイル = 1 公開クラスを基本とする

## 命名

| 対象               | 規約                                  | 例                                     |
| ------------------ | ------------------------------------- | -------------------------------------- |
| クラス             | UpperCamelCase, 名詞                  | `SyncIssuesApplicationService`         |
| 関数               | lowerCamelCase, 動詞 + 目的語          | `buildChunks`, `embedQuery`            |
| プロパティ         | lowerCamelCase                        | `ticketId`                             |
| Boolean            | `is/has/can` プレフィックス             | `isRunning`, `hasError`, `canRetry`    |
| 定数               | UPPER_SNAKE_CASE                      | `MAX_CHUNK_LENGTH = 1500`              |
| パッケージ         | lowercase                             | `com.example.redmineagent.domain.model` |

## マジック値の扱い

リテラル数値・文字列を直接コードに埋め込まない。

```kotlin
// ✗ NG
fun chunk(text: String) {
    if (text.length > 1500) { ... }
}

// ✓ OK (定数化)
companion object {
    private const val MAX_CHUNK_LENGTH = 1500
    private const val CHUNK_OVERLAP = 50
}

// ✓ OK (環境変数化、Infrastructure 層)
@Value("\${app.chunk.max-length}")
private val maxChunkLength: Int
```

## エラーハンドリング

- 例外は **原因が分かる粒度** で投げる (`IllegalStateException("...")` だけで済まさない)
- catch したら **何かする** (ログだけでも良い、握りつぶし禁止)
- リトライ可能な一時エラーと致命的エラーを区別

```kotlin
// ✗ NG
try { ... } catch (e: Exception) { }

// ✓ OK
try {
    redmineGateway.fetch()
} catch (e: TimeoutException) {
    logger.warn("Redmine timed out, will retry", e)
    throw RetriableException(e)
}
```

## ログ

- `logger` は `private val logger = LoggerFactory.getLogger(this::class.java)` でクラス内に
- `INFO`: 業務的に意味のあるイベント (同期完了、件数等)
- `DEBUG`: 詳細トレース
- `WARN`: 一時的な異常
- `ERROR`: 確定した失敗
- ログメッセージに変数を埋め込むときは **placeholder** を使う

```kotlin
// ✓ OK
logger.info("Sync completed: fetched={}, upserted={}", fetched, upserted)

// ✗ NG (常に文字列結合される)
logger.info("Sync completed: fetched=$fetched, upserted=$upserted")
```

## コメント

- **何をしているか** はコードで表現。コメントは **なぜそうしているか** に使う
- TODO は理由 + 担当 (or issue 番号) を併記: `// TODO(#123): foo を bar で置換`
- 外部仕様や非自明な制約への参照は OK: `// Redmine REST API は updated_on >= ISO で範囲指定`

## Kotlin 特有のスタイル

- **データクラス活用**: 値オブジェクト・DTO は `data class`
- **sealed class**: 取りうる値が有限な場合 (`AgentEvent` の Delta/Sources/Done/Error)
- **拡張関数**: ドメイン → DTO 変換などで活用 (`fun Issue.toDto(): IssueDto`)
- **trailing lambda**: `things.map { it.transform() }` (括弧外に出す)
- **string template** で文字列結合 (`+` を使わない)

## Spotless (ktlint 内包) / Detekt 2.x

- ビルドに組み込み済み: `task lint` で実行 (内部で `spotlessCheck` + `detekt`)
- フォーマット崩れは `./gradlew spotlessApply` で自動修正可能
- detekt 警告は **直す or `@Suppress` で抑制理由を明記**
- 抑制を多用しない (本質的な問題があるサイン)

## CI で落ちる条件

以下のいずれか 1 つでも fail すると CI は red:

- `./gradlew test` (単体・統合・ArchUnit すべて含む)
- `./gradlew spotlessCheck`
- `./gradlew detekt`
