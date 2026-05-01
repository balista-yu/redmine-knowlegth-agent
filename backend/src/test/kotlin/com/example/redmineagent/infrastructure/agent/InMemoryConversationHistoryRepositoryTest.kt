package com.example.redmineagent.infrastructure.agent

import com.example.redmineagent.domain.model.ChatMessage
import com.example.redmineagent.domain.model.ChatRole
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

/**
 * `InMemoryConversationHistoryRepository` の基本 CRUD と conversationId 単位の独立性を
 * 確認する単体テスト。
 */
class InMemoryConversationHistoryRepositoryTest :
    FunSpec({
        // 各テストで共有しないよう beforeTest で再生成する
        lateinit var repo: InMemoryConversationHistoryRepository
        beforeTest { repo = InMemoryConversationHistoryRepository() }

        test("未登録の conversationId は空リストを返す") {
            repo.get("unknown").shouldBeEmpty()
        }

        test("append したメッセージが順序通りに get で取得できる") {
            val cid = "c-1"
            repo.append(cid, ChatMessage(ChatRole.USER, "hello"))
            repo.append(cid, ChatMessage(ChatRole.ASSISTANT, "hi"))

            val history = repo.get(cid)

            history shouldHaveSize 2
            history[0].role shouldBe ChatRole.USER
            history[0].content shouldBe "hello"
            history[1].role shouldBe ChatRole.ASSISTANT
            history[1].content shouldBe "hi"
        }

        test("異なる conversationId は履歴を共有しない") {
            repo.append("c-A", ChatMessage(ChatRole.USER, "A1"))
            repo.append("c-B", ChatMessage(ChatRole.USER, "B1"))

            repo.get("c-A").map { it.content } shouldBe listOf("A1")
            repo.get("c-B").map { it.content } shouldBe listOf("B1")
        }

        test("clear で当該 conversationId の履歴が消える") {
            val cid = "c-X"
            repo.append(cid, ChatMessage(ChatRole.USER, "to-be-cleared"))

            repo.clear(cid)

            repo.get(cid).shouldBeEmpty()
        }
    })
