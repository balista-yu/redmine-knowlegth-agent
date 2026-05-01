package com.example.redmineagent.arch

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec

/**
 * オニオンアーキテクチャの依存方向 / アノテーション配置 / 命名規約を機械検査する。
 *
 * docs/01-design.md §6.6 の 7 ルール (Phase 1〜) + T-4-1 で追加する 3 ルール
 * (命名 + 配置の補強)。
 */
class OnionArchitectureTest :
    FunSpec({
        val classes =
            ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE)

        // ---------------------------------------------------------------------
        // 1. domain.* は外部フレームワークに依存しない
        // ---------------------------------------------------------------------
        test("domain は Spring Koog JPA Qdrant Hibernate に依存しない") {
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

        // ---------------------------------------------------------------------
        // 2. application.* は infrastructure.* に依存しない
        // ---------------------------------------------------------------------
        test("application は infrastructure に依存しない") {
            noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .check(classes)
        }

        // ---------------------------------------------------------------------
        // 3. infrastructure.web.* は domain.repository / domain.gateway を直接呼ばない
        // ---------------------------------------------------------------------
        test("infrastructure_web は domain repository gateway を直接呼ばない") {
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

        // ---------------------------------------------------------------------
        // 4. @Entity は infrastructure.persistence.entity.* のみ
        // ---------------------------------------------------------------------
        test("Entity アノテーションは infrastructure_persistence_entity のみ") {
            classes()
                .that()
                .areAnnotatedWith("jakarta.persistence.Entity")
                .should()
                .resideInAPackage("..infrastructure.persistence.entity..")
                .check(classes)
        }

        // ---------------------------------------------------------------------
        // 5. @RestController は infrastructure.web.* のみ
        // ---------------------------------------------------------------------
        test("RestController は infrastructure_web のみ") {
            classes()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .resideInAPackage("..infrastructure.web..")
                .check(classes)
        }

        // ---------------------------------------------------------------------
        // 6. @Configuration は infrastructure.config.* のみ
        // ---------------------------------------------------------------------
        test("Configuration は infrastructure_config のみ") {
            classes()
                .that()
                .areAnnotatedWith("org.springframework.context.annotation.Configuration")
                .should()
                .resideInAPackage("..infrastructure.config..")
                .check(classes)
        }

        // ---------------------------------------------------------------------
        // 7. @Scheduled は infrastructure.scheduler.* のみ
        // ---------------------------------------------------------------------
        test("Scheduled は infrastructure_scheduler のみ") {
            classes()
                .that()
                .areAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
                .should()
                .resideInAPackage("..infrastructure.scheduler..")
                .check(classes)
        }

        // ---------------------------------------------------------------------
        // T-4-1 追加ルール
        // ---------------------------------------------------------------------

        // 8. infrastructure.web.* の RestController クラス名は "Controller" で終わる
        test("RestController クラス名は Controller サフィックスで終わる") {
            classes()
                .that()
                .areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should()
                .haveSimpleNameEndingWith("Controller")
                .check(classes)
        }

        // 9. application.service.* のクラス名は "ApplicationService" で終わる
        //    (利便用 ad-hoc クラスがあれば例外として接尾なしを許可するが、本フェーズでは
        //     全 ApplicationService が規約準拠している前提)
        test("application_service の主要クラス名は ApplicationService サフィックスで終わる") {
            classes()
                .that()
                .resideInAPackage("..application.service..")
                .and()
                .arePublic()
                .and()
                .areTopLevelClasses()
                .should()
                .haveSimpleNameEndingWith("ApplicationService")
                .check(classes)
        }

        // 10. JPA Entity (@Entity 付き) は infrastructure.persistence.entity.* に
        //     置かれており、かつクラス名が "Entity" で終わる (DB マッピング用と一目で分かる)
        test("Entity アノテーションを持つクラスはクラス名末尾が Entity") {
            classes()
                .that()
                .areAnnotatedWith("jakarta.persistence.Entity")
                .should()
                .haveSimpleNameEndingWith("Entity")
                .check(classes)
        }
    }) {
    companion object {
        private const val BASE_PACKAGE = "com.example.redmineagent"
    }
}
