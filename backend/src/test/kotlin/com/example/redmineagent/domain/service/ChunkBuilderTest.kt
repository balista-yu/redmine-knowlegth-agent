package com.example.redmineagent.domain.service

import com.example.redmineagent.domain.model.ChunkType
import com.example.redmineagent.testdata.IssueFixture
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.junit.jupiter.api.Test

class ChunkBuilderTest {
    @Test
    fun `通常 - description と journals 3 件で 4 チャンク`() {
        val issue =
            IssueFixture.issue(
                description = "desc body",
                journals =
                    listOf(
                        IssueFixture.journal("note1"),
                        IssueFixture.journal("note2"),
                        IssueFixture.journal("note3"),
                    ),
            )

        val chunks = ChunkBuilder.build(issue)

        chunks shouldHaveSize 4
        chunks[0].chunkType shouldBe ChunkType.DESCRIPTION
        chunks[0].chunkIndex shouldBe 0
        chunks.drop(1).forAll { it.chunkType shouldBe ChunkType.JOURNAL }
        chunks.map { it.chunkIndex } shouldBe listOf(0, 1, 2, 3)
        chunks.forAll { it.subIndex shouldBe 0 }
    }

    @Test
    fun `description のみで 1 チャンク`() {
        val issue = IssueFixture.issue(description = "only desc")

        val chunks = ChunkBuilder.build(issue)

        chunks shouldHaveSize 1
        chunks[0].chunkType shouldBe ChunkType.DESCRIPTION
        chunks[0].chunkIndex shouldBe 0
    }

    @Test
    fun `description blank で journals だけがチャンク化され index は 1 始まり`() {
        val issue =
            IssueFixture.issue(
                description = "   ",
                journals =
                    listOf(
                        IssueFixture.journal("note1"),
                        IssueFixture.journal(""),
                        IssueFixture.journal("note2"),
                        IssueFixture.journal(null),
                        IssueFixture.journal("note3"),
                    ),
            )

        val chunks = ChunkBuilder.build(issue)

        chunks shouldHaveSize 3
        chunks.forAll { it.chunkType shouldBe ChunkType.JOURNAL }
        chunks.map { it.chunkIndex } shouldBe listOf(1, 2, 3)
    }

    @Test
    fun `description と journals が全て空なら 0 チャンク`() {
        val issue =
            IssueFixture.issue(
                description = null,
                journals =
                    listOf(
                        IssueFixture.journal(""),
                        IssueFixture.journal(null),
                        IssueFixture.journal("   "),
                    ),
            )

        val chunks = ChunkBuilder.build(issue)

        chunks shouldHaveSize 0
    }

    @Test
    fun `4000 文字の description は subIndex 連番で複数 chunk に分割される`() {
        val text = "a".repeat(4000)
        val issue = IssueFixture.issue(description = text)

        val chunks = ChunkBuilder.build(issue)

        chunks.size shouldBeGreaterThan 1
        chunks.forAll {
            it.chunkType shouldBe ChunkType.DESCRIPTION
            it.chunkIndex shouldBe 0
        }
        chunks.map { it.subIndex } shouldBe (0 until chunks.size).toList()
    }

    @Test
    fun `同じ chunkType と content なら content_hash も一致`() {
        val issue1 = IssueFixture.issue(ticketId = 1, description = "same content")
        val issue2 = IssueFixture.issue(ticketId = 2, description = "same content")

        val hash1 = ChunkBuilder.build(issue1).single().contentHash
        val hash2 = ChunkBuilder.build(issue2).single().contentHash

        hash1 shouldBe hash2
    }

    @Test
    fun `1500 文字超は改行優先で分割し、改行なしは 1500 文字で fallback 分割`() {
        // 改行ありケース: 1300 文字 + LF + 300 文字 (合計 1601 文字)
        // → 1 個目 chunk は改行で終端 (length 1301)
        val withNewline = "a".repeat(1300) + "\n" + "b".repeat(300)
        val chunksWithNewline =
            ChunkBuilder.build(IssueFixture.issue(description = withNewline))

        chunksWithNewline.size shouldBeGreaterThan 1
        chunksWithNewline[0].content shouldEndWith "\n"
        chunksWithNewline[0].content.length shouldBe 1301

        // 改行なしケース: 1601 文字すべて 'a'
        // → 1 個目 chunk はちょうど 1500 文字 (文字数 fallback)
        val noNewline = "a".repeat(1601)
        val chunksNoNewline =
            ChunkBuilder.build(IssueFixture.issue(description = noNewline))

        chunksNoNewline.size shouldBeGreaterThan 1
        chunksNoNewline[0].content.length shouldBe 1500
    }
}
