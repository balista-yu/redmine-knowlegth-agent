package com.example.redmineagent.infrastructure.web

import com.example.redmineagent.application.service.AnswerQuestionApplicationService
import com.example.redmineagent.domain.model.AgentEvent
import com.example.redmineagent.infrastructure.web.dto.ChatRequest
import com.example.redmineagent.infrastructure.web.mapper.toServerSentEvent
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
 * F-04 (チャット応答) の REST API (詳細: docs/03-api-spec.md §1)。
 *
 * - POST `/api/chat` (JSON 入力) → SSE で `delta`/`sources`/`done`/`error` を発行
 * - Bean Validation: `message` 1〜4000 文字 (`ChatRequest`)
 * - validation 失敗は Spring 標準ハンドラで 400 (SSE 開始前)
 * - SSE Flow が例外終端した場合も `error` フレームを保険発行して UI 側のハングを回避
 *
 * `conversationId` は省略時にコントローラ側で UUID v4 を採番し、`done` イベントの payload と
 * `Application` への入力の両方で同じ値を使う (採番タイミング: コントローラのほうが早い)。
 */
@RestController
@RequestMapping("/api")
class ChatController(
    @Lazy private val answerQuestion: AnswerQuestionApplicationService,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @PostMapping("/chat", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
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
