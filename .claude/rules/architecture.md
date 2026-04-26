# アーキテクチャルール (常時適用)

このルールは backend のすべての作業で常に適用される。

## オニオンアーキテクチャ 4 層

```
Domain Model (中心)
  └→ Domain Services (interface 定義 + 純粋ロジック)
       └→ Application Services (ユースケース)
            └→ Infrastructure (web / persistence / external / agent / config / scheduler)
```

依存方向は **外→内のみ**。逆向きの import は ArchUnit で検出され CI で落ちる。

## 各層の責務 (要約)

| 層              | 何を置く                                                  | import してよい                 |
| --------------- | --------------------------------------------------------- | ------------------------------- |
| Domain Model    | Entity / Value Object                                      | kotlin.* / kotlinx.* のみ        |
| Domain Services | interface (Repository / Gateway / Service) + 純粋ロジック    | Domain Model のみ                |
| Application     | ApplicationService (ユースケース実装)                       | Domain のみ                       |
| Infrastructure  | interface 実装 / Controller / Scheduler / JPA / SDK 連携     | 全レイヤ + 任意のサードパーティ可 |

## interface 命名規約

外部システムへの依存抽象は Domain 層に置く。種類によって配置先が変わる:

| 用途                            | 配置先                  | サフィックス例     |
| ------------------------------- | ----------------------- | ------------------ |
| 永続化されたコレクション        | `domain/repository/`    | `*Repository`      |
| 外部システムへの問い合わせ      | `domain/gateway/`       | `*Gateway`         |
| 機能的サービス (embed / chat 等) | `domain/gateway/`       | `*Service`, `*Model` |

## 実装クラスの配置と命名

interface の実装は Infrastructure 層に置く。命名は `Jpa~Repository`, `Qdrant~Repository`, `RedmineHttpGateway` のように **技術プレフィックス + 役割サフィックス**。

## Application Service の命名

ユースケースは `<動詞>+<目的語>+ApplicationService` で命名する。

- ✓ `SyncIssuesApplicationService`, `AnswerQuestionApplicationService`, `ReconcileApplicationService`
- ✗ `SyncService`, `IssueService`, `AppService` (汎用過ぎる)

## DTO の境界

- Infrastructure 層の DTO (例: `IssueDto`) と Domain モデル (例: `Issue`) は **必ず分離**
- 境界 (Mapper) でだけ変換する
- Domain 層に Spring の `@JsonProperty` 等を持ち込まない

## Spring アノテーションの境界

| アノテーション             | 配置可能な層                       |
| -------------------------- | ---------------------------------- |
| `@Component`, `@Service`, `@Repository`, `@RestController`, `@Scheduled` | Infrastructure 層のみ |
| `@Entity`, `@Column`, `@Id` (JPA)                                       | `infrastructure/persistence/entity/` のみ |
| `@Configuration`, `@Bean`                                                | `infrastructure/config/` のみ |

Application Service もコンストラクタインジェクションを受けるが、`@Service` を付けるのは Infrastructure 層からの DI 配線時のみ (実際には `@Component` または `@Service` を付けて Spring の管理下に入る — これは Spring の DI 機構を使うための妥協)。

> 妥協点: Application Service クラスに `@Service` を付与することは許可する。これは「Application Services が Spring の DI コンテナで管理されるための配線」であり、思想的には config 層の責務だが、Kotlin/Spring の慣習上クラス側に書く。
> ただしロジック内で Spring の他の機能 (`@Transactional` 等) を使うのは禁止。トランザクション境界は Infrastructure 層の `@Transactional` メソッドで明示的に切る。

## 参照ドキュメント

- 詳細設計: `docs/01-design.md` §6 (アーキテクチャ)
- ディレクトリ構成: `docs/01-design.md` §6.4
- ArchUnit ルール: `docs/01-design.md` §6.5
