package com.example.redmineagent.domain.service

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test

class HashCalculatorTest {
    private val calculator = HashCalculator()

    @Test
    fun `sha256Hex は hello に対する既知ハッシュを返す`() {
        // 既知値: openssl dgst -sha256 などで同一値を確認可能
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        calculator.sha256Hex("hello") shouldBe expected
    }

    @Test
    fun `sha256Hex は空文字列に対する既知ハッシュを返す`() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        calculator.sha256Hex("") shouldBe expected
    }

    @Test
    fun `同じ入力なら毎回同じハッシュを返す (決定性)`() {
        val input = "redmine:issue:42:chunk:0"
        calculator.sha256Hex(input) shouldBe calculator.sha256Hex(input)
    }

    @Test
    fun `日本語などの UTF-8 multi-byte 文字でも 64 文字 hex を返す`() {
        val hash = calculator.sha256Hex("こんにちは Redmine")
        hash shouldHaveLength 64
        hash shouldMatch Regex("[0-9a-f]{64}")
    }
}
