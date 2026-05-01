package com.example.redmineagent.domain.service

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldMatch

class HashCalculatorTest :
    FunSpec({
        test("sha256Hex は hello に対する既知ハッシュを返す") {
            // 既知値: openssl dgst -sha256 などで同一値を確認可能
            val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
            HashCalculator.sha256Hex("hello") shouldBe expected
        }

        test("sha256Hex は空文字列に対する既知ハッシュを返す") {
            val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            HashCalculator.sha256Hex("") shouldBe expected
        }

        test("同じ入力なら毎回同じハッシュを返す (決定性)") {
            val input = "redmine:issue:42:chunk:0"
            HashCalculator.sha256Hex(input) shouldBe HashCalculator.sha256Hex(input)
        }

        test("日本語などの UTF-8 multi-byte 文字でも 64 文字 hex を返す") {
            val hash = HashCalculator.sha256Hex("こんにちは Redmine")
            hash shouldHaveLength 64
            hash shouldMatch Regex("[0-9a-f]{64}")
        }
    })
