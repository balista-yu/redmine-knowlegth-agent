package com.example.redmineagent.infrastructure.web

import com.example.redmineagent.application.service.AnswerQuestionApplicationService
import com.example.redmineagent.domain.model.AgentEvent
import com.example.redmineagent.domain.model.TicketHit
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch

/**
 * `ChatController` の SSE / validation 統合テスト (T-2-4 DoD)。
 *
 * 検証ポイント:
 *  - SSE のイベント順序: `delta`* → `done`
 *  - validation: 空 message で 400
 *  - ApplicationService 例外 → SSE で `error` イベント
 *  - Sources 経路: `Sources` イベントが `sources` SSE に変換される
 */
@WebMvcTest(controllers = [ChatController::class])
@Import(ChatControllerTest.MockBeans::class)
class ChatControllerTest
    @Autowired
    constructor(
        private val mvc: MockMvc,
        private val answerQuestion: AnswerQuestionApplicationService,
    ) {
        @BeforeEach
        fun resetMocks() {
            clearMocks(answerQuestion)
        }

        @Test
        fun `正常系 - delta done が SSE で順序通りに返る`() {
            every { answerQuestion.execute(any(), any()) } returns
                flowOf(
                    AgentEvent.Delta("Hello, "),
                    AgentEvent.Delta("world"),
                    AgentEvent.Done,
                )

            val mvcResult =
                mvc
                    .post("/api/chat") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"message":"hi","conversationId":"c-1"}"""
                    }.andReturn()

            val body =
                mvc
                    .perform(asyncDispatch(mvcResult))
                    .andReturn()
                    .response.contentAsString

            // SSE フレームは "event: ...\ndata: ...\n\n" の形
            body shouldContain "event:delta"
            body shouldContain "\"text\":\"Hello, \""
            body shouldContain "\"text\":\"world\""
            body shouldContain "event:done"
            body shouldContain "\"conversationId\":\"c-1\""
            // delta が done より前に出ること
            val deltaIdx = body.indexOf("event:delta")
            val doneIdx = body.indexOf("event:done")
            (deltaIdx < doneIdx) shouldBe true
        }

        @Test
        fun `Sources イベントは sources SSE に変換される`() {
            every { answerQuestion.execute(any(), any()) } returns
                flowOf(
                    AgentEvent.Delta("answer"),
                    AgentEvent.Sources(
                        listOf(
                            TicketHit(
                                ticketId = 128,
                                projectName = "infra",
                                tracker = "Bug",
                                status = "Closed",
                                subject = "tls cert",
                                url = "http://redmine.example/issues/128",
                                score = 0.83f,
                                snippet = "Let's Encrypt 自動更新",
                            ),
                        ),
                    ),
                    AgentEvent.Done,
                )

            val mvcResult =
                mvc
                    .post("/api/chat") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"message":"how to fix tls","conversationId":"c-2"}"""
                    }.andReturn()

            val body =
                mvc
                    .perform(asyncDispatch(mvcResult))
                    .andReturn()
                    .response.contentAsString

            body shouldContain "event:sources"
            body shouldContain "\"ticketId\":128"
            body shouldContain "\"subject\":\"tls cert\""
        }

        @Test
        fun `空 message は 400 で SSE が始まらない`() {
            mvc
                .post("/api/chat") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"message":"","conversationId":null}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `4001 文字の message は 400`() {
            val tooLong = "a".repeat(4001)
            mvc
                .post("/api/chat") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"message":"$tooLong"}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `ApplicationService が AgentEvent Error を発行すると SSE error イベントになる`() {
            every { answerQuestion.execute(any(), any()) } returns
                flowOf(
                    AgentEvent.Error(code = "OLLAMA_UNAVAILABLE", message = "ollama unreachable"),
                )

            val mvcResult =
                mvc
                    .post("/api/chat") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"message":"hi","conversationId":"c-3"}"""
                    }.andReturn()

            val body =
                mvc
                    .perform(asyncDispatch(mvcResult))
                    .andReturn()
                    .response.contentAsString

            body shouldContain "event:error"
            body shouldContain "\"code\":\"OLLAMA_UNAVAILABLE\""
        }

        @Test
        fun `Flow 自体が例外で終端した場合は INTERNAL error フレームが保険発行される`() {
            every { answerQuestion.execute(any(), any()) } returns
                flow<AgentEvent> {
                    @Suppress("TooGenericExceptionThrown") // テストで Flow 終端例外を再現するため任意例外を投げる
                    throw RuntimeException("unexpected boom")
                }

            val mvcResult =
                mvc
                    .post("/api/chat") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"message":"hi","conversationId":"c-4"}"""
                    }.andReturn()

            val body =
                mvc
                    .perform(asyncDispatch(mvcResult))
                    .andReturn()
                    .response.contentAsString

            body shouldContain "event:error"
            body shouldContain "\"code\":\"INTERNAL\""
        }

        @Test
        fun `conversationId 未指定なら UUID が採番されて Application に渡る`() {
            val cidSlot = slot<String>()
            every { answerQuestion.execute(any(), capture(cidSlot)) } returns flowOf(AgentEvent.Done)

            val mvcResult =
                mvc
                    .post("/api/chat") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"message":"hi"}"""
                    }.andReturn()
            mvc.perform(asyncDispatch(mvcResult))

            // UUID v4 = 36 文字 (ハイフン区切り)
            cidSlot.captured.length shouldBe 36
        }

        @TestConfiguration
        class MockBeans {
            @Bean
            fun answerQuestionApplicationService(): AnswerQuestionApplicationService = mockk(relaxed = true)
        }
    }
