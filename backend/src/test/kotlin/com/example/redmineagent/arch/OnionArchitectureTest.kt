package com.example.redmineagent.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * オニオンアーキテクチャの依存方向 / アノテーション配置を機械検査する。
 *
 * docs/01-design.md §6.6 (7 ルール) に対応。Phase 1 以降の実装で違反が
 * 入った時点で CI が落ちるよう、最初からこの 7 ルールを enable しておく。
 */
class OnionArchitectureTest {
    private val classes =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(BASE_PACKAGE)

    // -------------------------------------------------------------------------
    // 1. domain.* は外部フレームワークに依存しない
    // -------------------------------------------------------------------------
    @Test
    fun `domain は Spring Koog JPA Qdrant Hibernate に依存しない`() {
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "org.springframework..",
                "ai.koog..",
                "jakarta.persistence..",
                "io.qdrant..",
                "org.hibernate..",
            ).check(classes)
    }

    // -------------------------------------------------------------------------
    // 2. application.* は infrastructure.* に依存しない
    // -------------------------------------------------------------------------
    @Test
    fun `application は infrastructure に依存しない`() {
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .check(classes)
    }

    // -------------------------------------------------------------------------
    // 3. infrastructure.web.* は domain.repository / domain.gateway を直接呼ばない
    // -------------------------------------------------------------------------
    @Test
    fun `infrastructure_web は domain repository gateway を直接呼ばない`() {
        noClasses()
            .that()
            .resideInAPackage("..infrastructure.web..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..domain.repository..",
                "..domain.gateway..",
            ).check(classes)
    }

    // -------------------------------------------------------------------------
    // 4. @Entity は infrastructure.persistence.entity.* のみ
    // -------------------------------------------------------------------------
    @Test
    fun `Entity アノテーションは infrastructure_persistence_entity のみ`() {
        classes()
            .that()
            .areAnnotatedWith("jakarta.persistence.Entity")
            .should()
            .resideInAPackage("..infrastructure.persistence.entity..")
            .check(classes)
    }

    // -------------------------------------------------------------------------
    // 5. @RestController は infrastructure.web.* のみ
    // -------------------------------------------------------------------------
    @Test
    fun `RestController は infrastructure_web のみ`() {
        classes()
            .that()
            .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should()
            .resideInAPackage("..infrastructure.web..")
            .check(classes)
    }

    // -------------------------------------------------------------------------
    // 6. @Configuration は infrastructure.config.* のみ
    // -------------------------------------------------------------------------
    @Test
    fun `Configuration は infrastructure_config のみ`() {
        classes()
            .that()
            .areAnnotatedWith("org.springframework.context.annotation.Configuration")
            .should()
            .resideInAPackage("..infrastructure.config..")
            .check(classes)
    }

    // -------------------------------------------------------------------------
    // 7. @Scheduled は infrastructure.scheduler.* のみ
    // -------------------------------------------------------------------------
    @Test
    fun `Scheduled は infrastructure_scheduler のみ`() {
        classes()
            .that()
            .areAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
            .should()
            .resideInAPackage("..infrastructure.scheduler..")
            .check(classes)
    }

    companion object {
        private const val BASE_PACKAGE = "com.example.redmineagent"
    }
}
