package com.example.redmineagent.infrastructure.web

import com.example.redmineagent.application.service.AnswerQuestionApplicationService
import com.example.redmineagent.domain.model.AgentEvent
import com.example.redmineagent.infrastructure.web.dto.ApiErrorDto
import com.example.redmineagent.infrastructure.web.dto.ChatDeltaDto
import com.example.redmineagent.infrastructure.web.dto.ChatDoneDto
import com.example.redmineagent.infrastructure.web.dto.ChatErrorDto
import com.example.redmineagent.infrastructure.web.dto.ChatRequest
import com.example.redmineagent.infrastructure.web.dto.ChatSourcesDto
import com.example.redmineagent.infrastructure.web.mapper.toServerSentEvent
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * F-04 (チャット応答) の REST API。
 *
 * - POST `/api/chat` (JSON 入力) → SSE で `delta`/`sources`/`done`/`error` を発行
 * - Bean Validation: `message` 1〜4000 文字 (`ChatRequest`)
 * - validation 失敗は Spring 標準ハンドラで 400 (SSE 開始前)
 * - SSE Flow が例外終端した場合も `error` フレームを保険発行して UI 側のハングを回避
 *
 * `conversationId` は省略時にコントローラ側で UUID v4 を採番し、`done` イベントの payload と
 * `Application` への入力の両方で同じ値を使う (採番タイミング: コントローラのほうが早い)。
 *
 * SSE のスキーマは `Flow<ServerSentEvent<Any>>` から springdoc が型を引けないため、
 * `@ApiResponse(content = ...)` で 4 種 (Delta/Sources/Done/Error) を `oneOf` 列挙する。
 */
@RestController
@RequestMapping("/api")
@Tag(name = "chat", description = "チャット (Server-Sent Events) エンドポイント")
class ChatController(
    @Lazy private val answerQuestion: AnswerQuestionApplicationService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(
        summary = "エージェントに質問して SSE で回答を受け取る",
        description = """
            イベント順序:
              1. `delta` を 0 回以上 (LLM トークン断片)
              2. `sources` を 0 〜 1 回 (引用元チケット一覧)
              3. `done` を 1 回 (正常終了) または `error` を 1 回 (異常終了)

            `conversationId` を省略するとサーバ側で UUID v4 を採番し、`done` イベントに含めて返す。
        """,
    )
    @ApiResponse(
        responseCode = "200",
        description = "SSE ストリーム。各イベントの data は下記いずれか",
        content = [
            Content(
                mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                schema =
                    Schema(
                        oneOf = [
                            ChatDeltaDto::class,
                            ChatSourcesDto::class,
                            ChatDoneDto::class,
                            ChatErrorDto::class,
                        ],
                    ),
            ),
        ],
    )
    @ApiResponse(
        responseCode = "400",
        description = "message が空 / 4000 文字超過",
        content = [Content(schema = Schema(implementation = ApiErrorDto::class))],
    )
    fun chat(
        @Valid @RequestBody request: ChatRequest,
    ): Flow<ServerSentEvent<Any>> {
        val effectiveCid = request.conversationId ?: UUID.randomUUID().toString()
        logger.info("Chat stream starting: cid={}", effectiveCid)
        return answerQuestion
            .execute(message = request.message, conversationId = effectiveCid)
            .map { it.toServerSentEvent(effectiveCid) }
            .catch { e ->
                // ApplicationService 内で AgentEvent.Error に変換される想定だが、Flow 自体が
                // 例外で終端した場合の保険として INTERNAL Error フレームを発行する
                logger.error("Chat stream terminated with exception: cid={}, error={}", effectiveCid, e.message, e)
                emit(
                    AgentEvent
                        .Error(code = CODE_INTERNAL, message = e.message ?: e::class.java.simpleName)
                        .toServerSentEvent(effectiveCid),
                )
            }
    }

    companion object {
        private const val CODE_INTERNAL = "INTERNAL"
    }
}
