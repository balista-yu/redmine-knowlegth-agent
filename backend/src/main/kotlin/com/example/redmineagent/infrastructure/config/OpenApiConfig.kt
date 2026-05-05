package com.example.redmineagent.infrastructure.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * springdoc-openapi で生成される OpenAPI 仕様のメタ情報を定義する。
 *
 * 個別エンドポイントの `@Operation` / `@ApiResponse` アノテーションは
 * `infrastructure/web/` 配下の各 Controller に付与する。ここでは横断的な
 * Info / Server / Tag のみを宣言する。
 *
 * 生成された `/v3/api-docs.yaml` は GitHub Actions (`openapi-sync.yml`) で
 * `docs/openapi.yaml` に自動コミットされる。
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(
        @Value("\${spring.application.name}") applicationName: String,
        @Value("\${server.port:8080}") serverPort: Int,
    ): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("Redmine Knowledge Agent API")
                    .version(API_VERSION)
                    .description(
                        """
                        Redmine のチケットを RAG ナレッジ源とする AI エージェント API。

                        - チャット: `/api/chat` (Server-Sent Events で逐次回答)
                        - 同期管理: `/api/sync`, `/api/reconcile`, `/api/sync/status`, `/api/sync/runs`

                        SSE のイベント順序など振る舞い契約は `docs/01-design.md` を参照。
                        """.trimIndent(),
                    ).license(License().name("Internal").url("https://example.com")),
            ).servers(
                listOf(
                    Server().url("http://localhost:$serverPort").description("Local development ($applicationName)"),
                ),
            ).tags(
                listOf(
                    Tag().name(TAG_CHAT).description("チャット (SSE) エンドポイント"),
                    Tag().name(TAG_SYNC).description("Redmine → Qdrant 同期 / 状態確認"),
                ),
            )

    companion object {
        const val TAG_CHAT = "chat"
        const val TAG_SYNC = "sync"
        private const val API_VERSION = "0.1.0"
    }
}
