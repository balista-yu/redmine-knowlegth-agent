package com.example.redmineagent.application.service

import ai.koog.agents.core.agent.AIAgent
import com.example.redmineagent.application.exception.OllamaUnavailableException
import com.example.redmineagent.application.exception.QdrantUnavailableException
import com.example.redmineagent.domain.model.AgentEvent
import com.example.redmineagent.domain.model.ChatMessage
import com.example.redmineagent.domain.model.ChatRole
import com.example.redmineagent.domain.repository.ConversationHistoryRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.toList

class AnswerQuestionApplicationServiceTest :
    FunSpec({
        val agent = mockk<AIAgent<String, String>>()
        val history = mockk<ConversationHistoryRepository>(relaxed = true)
        val service = AnswerQuestionApplicationService(agent, history)

        test("正常系 - Delta と Done が順に発行され履歴に user assistant が追記される") {
            coEvery { agent.run("hello") } returns "hi there"
            val cidSlot = slot<String>()
            val msgSlot = slot<ChatMessage>()
            every { history.append(capture(cidSlot), capture(msgSlot)) } returns Unit

            val events = service.execute("hello", conversationId = "c-1").toList()

            events shouldHaveSize 2
            (events[0] as AgentEvent.Delta).text shouldBe "hi there"
            events[1] shouldBe AgentEvent.Done
            // history.append は user / assistant の 2 回呼ばれる
            verify(exactly = 2) { history.append(any(), any()) }
        }

        test("conversationId が null なら UUID が採番されて履歴に保存される") {
            coEvery { agent.run(any()) } returns "ok"
            val cidSlot = slot<String>()
            every { history.append(capture(cidSlot), any()) } returns Unit

            service.execute("anything", conversationId = null).toList()

            // UUID v4 は 36 文字のハイフン区切り
            cidSlot.captured.length shouldBe 36
            cidSlot.captured shouldBe cidSlot.captured.lowercase()
        }

        test("OllamaUnavailableException → OLLAMA_UNAVAILABLE Error イベントに変換される") {
            coEvery { agent.run(any()) } throws OllamaUnavailableException("ollama unreachable")

            val events = service.execute("hello", conversationId = "c-2").toList()

            events shouldHaveSize 1
            val error = events[0].shouldBeInstanceOf<AgentEvent.Error>()
            error.code shouldBe "OLLAMA_UNAVAILABLE"
        }

        test("QdrantUnavailableException → QDRANT_UNAVAILABLE Error イベントに変換される") {
            coEvery { agent.run(any()) } throws QdrantUnavailableException("qdrant down")

            val events = service.execute("hello", conversationId = "c-2q").toList()

            (events.single() as AgentEvent.Error).code shouldBe "QDRANT_UNAVAILABLE"
        }

        test("その他の RuntimeException は INTERNAL Error イベントに変換される") {
            coEvery { agent.run(any()) } throws RuntimeException("something went wrong")

            val events = service.execute("hello", conversationId = "c-3").toList()

            (events.single() as AgentEvent.Error).code shouldBe "INTERNAL"
        }

        test("エラー時も user メッセージは履歴に保存されるが assistant は保存されない") {
            coEvery { agent.run(any()) } throws RuntimeException("boom")
            val msgs = mutableListOf<ChatMessage>()
            every { history.append(any(), capture(msgs)) } returns Unit

            service.execute("a question", conversationId = "c-4").toList()

            // user 1 件のみ、assistant は append されない
            msgs shouldHaveSize 1
            msgs[0].role shouldBe ChatRole.USER
        }
    })
