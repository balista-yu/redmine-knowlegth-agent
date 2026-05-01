package com.example.redmineagent.infrastructure.agent

import com.example.redmineagent.domain.model.ChatMessage
import com.example.redmineagent.domain.model.ChatRole
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * `ConversationHistoryStore` の基本 CRUD と conversationId 単位の独立性を確認する単体テスト。
 */
class ConversationHistoryStoreTest {
    private val store = ConversationHistoryStore()

    @Test
    fun `未登録の conversationId は空リストを返す`() {
        store.get("unknown").shouldBeEmpty()
    }

    @Test
    fun `append したメッセージが順序通りに get で取得できる`() {
        val cid = "c-1"
        store.append(cid, ChatMessage(ChatRole.USER, "hello"))
        store.append(cid, ChatMessage(ChatRole.ASSISTANT, "hi"))

        val history = store.get(cid)

        history shouldHaveSize 2
        history[0].role shouldBe ChatRole.USER
        history[0].content shouldBe "hello"
        history[1].role shouldBe ChatRole.ASSISTANT
        history[1].content shouldBe "hi"
    }

    @Test
    fun `異なる conversationId は履歴を共有しない`() {
        store.append("c-A", ChatMessage(ChatRole.USER, "A1"))
        store.append("c-B", ChatMessage(ChatRole.USER, "B1"))

        store.get("c-A").map { it.content } shouldBe listOf("A1")
        store.get("c-B").map { it.content } shouldBe listOf("B1")
    }

    @Test
    fun `clear で当該 conversationId の履歴が消える`() {
        val cid = "c-X"
        store.append(cid, ChatMessage(ChatRole.USER, "to-be-cleared"))

        store.clear(cid)

        store.get(cid).shouldBeEmpty()
    }
}
