// =============================================================================
// build.gradle.kts: Redmine Knowledge Agent backend
// =============================================================================
// 依存方針: docs/02-tasks.md T-1-1 / docs/01-design.md §2.3 準拠
// 静的解析: Spotless (ktlint 内包) + Detekt 2.x
// =============================================================================
import dev.detekt.gradle.Detekt

plugins {
    val kotlinVersion = "2.3.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    // RagSearchTool の @Serializable / KSerializer 自動生成用 (T-2-2)
    kotlin("plugin.serialization") version kotlinVersion

    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"

    id("com.diffplug.spotless") version "8.4.0"
    id("dev.detekt") version "2.0.0-alpha.3"

    jacoco
}

group = "com.example"
version = "0.1.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        // -Xjsr305=strict: JSR-305 (@Nullable 等) の null 性を strict 扱い
        // -Xannotation-default-target=param-property:
        //   Kotlin 2.3 で各種アノテーション (@Lazy, @Value, @JsonProperty 等) のデフォルト
        //   ターゲットが param-property (param + field) に変わる予定。明示しないと
        //   遷移期間中の "annotation will also be applied to field" 警告が大量に出る
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xannotation-default-target=param-property",
        )
    }
}

repositories {
    mavenCentral()
}

// 共通バージョン定数
object Versions {
    const val KOOG = "0.8.0"
    const val QDRANT = "1.17.0"
    const val ARCHUNIT = "1.4.2"
    const val MOCKK = "1.14.9"
    const val KOTEST = "6.1.11"
    const val TESTCONTAINERS = "2.0.5"
    const val COROUTINES = "1.10.2"
    const val FLYWAY_PG = "12.5.0"
}

dependencies {
    // --- Spring Boot ----------------------------------------------------------
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // --- Persistence ----------------------------------------------------------
    // Spring Boot 4.0 では Flyway 自動設定が starter-flyway モジュールに分離された
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql:${Versions.FLYWAY_PG}")
    runtimeOnly("org.postgresql:postgresql")

    // --- Koog (AI agent + RAG + Ollama) --------------------------------------
    // Koog 0.8.x の実アーティファクト名 (multiplatform。Gradle が JVM 派生を解決する)
    // 注: embeddings-llm は意図的に未依存。Koog 0.8.0 の OllamaClient.embed は
    //     旧 /api/embeddings を叩くがレスポンス DTO は新形式を期待しており壊れているため、
    //     OllamaEmbeddingService が Spring WebClient で直接 /api/embed を叩く実装を持つ。
    //     upstream main で #1854 / #1885 修正済み。次回バージョンアップ時に再依存を検討。
    implementation("ai.koog:koog-agents-jvm:${Versions.KOOG}")
    implementation("ai.koog:rag-vector:${Versions.KOOG}")
    implementation("ai.koog:prompt-executor-ollama-client:${Versions.KOOG}")

    // --- Qdrant ---------------------------------------------------------------
    implementation("io.qdrant:client:${Versions.QDRANT}")

    // --- Kotlin / coroutines --------------------------------------------------
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:${Versions.COROUTINES}")
    // Jackson 3.x (Spring Boot 4.0 から `JacksonJsonDecoder` 等が tools.jackson 系を要求)
    // Jackson 2.x モジュールは Spring Boot Json 4 が後方互換のため引き続きスターター経由で残る
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // --- Test -----------------------------------------------------------------
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.mockito") // MockK を使うため
    }
    // Spring Boot 4.0 で @WebMvcTest は別 starter (spring-boot-starter-webmvc-test) に分離された
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("io.mockk:mockk:${Versions.MOCKK}")
    testImplementation("io.kotest:kotest-assertions-core:${Versions.KOTEST}")
    // Kotest 6 を JUnit Platform 経由で実行するランナー (FunSpec 等の Spec を検出)
    testImplementation("io.kotest:kotest-runner-junit5:${Versions.KOTEST}")
    // Testcontainers 2.x で submodule 名が "testcontainers-*" に統一された
    testImplementation("org.testcontainers:testcontainers:${Versions.TESTCONTAINERS}")
    testImplementation("org.testcontainers:testcontainers-postgresql:${Versions.TESTCONTAINERS}")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter:${Versions.TESTCONTAINERS}")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.COROUTINES}")
    testImplementation("com.tngtech.archunit:archunit-junit5:${Versions.ARCHUNIT}")
}

// =============================================================================
// Test (`*Test` suffix = unit) と integrationTest (`*IT` suffix) を分離。
//   CI の `test` ジョブはユニット限定。Testcontainers 系は `integrationTest` で
//   Docker daemon があるホストで明示的に走らせる方針 (ローカルおよび手動 CI step)。
//
// JVM 25 で grpc-netty-shaded (Qdrant client 経由) が出すランタイム警告を抑制:
//   - `--enable-native-access=ALL-UNNAMED`: native lib load (System.loadLibrary)
//   - `-Xshare:off`: bootstrap classpath 追加時の CDS Sharing 警告
//   - `--sun-misc-unsafe-memory-access=allow`: JEP 471 で導入された
//     sun.misc.Unsafe::allocateMemory 警告 (Netty 内部) を黙らせる
// =============================================================================
val testJvmArgs =
    listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Xshare:off",
        "--sun-misc-unsafe-memory-access=allow",
    )

tasks.test {
    useJUnitPlatform()
    jvmArgs = testJvmArgs
    filter {
        excludeTestsMatching("*IT")
    }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.register<Test>("integrationTest") {
    description = "Runs *IT tests (Testcontainers / 外部依存あり)"
    group = "verification"
    useJUnitPlatform()
    jvmArgs = testJvmArgs
    filter {
        includeTestsMatching("*IT")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)
    testLogging {
        events("failed", "skipped", "passed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = true
    }
}

// =============================================================================
// JaCoCo (本フェーズは閾値ゲートなし。レポート生成のみ)
// =============================================================================
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

// =============================================================================
// Spotless (ktlint 内包) — formatting check
// =============================================================================
spotless {
    kotlin {
        ktlint("1.8.0")
        target("src/**/*.kt")
        targetExclude("build/**", "**/generated/**")
    }
    kotlinGradle {
        ktlint("1.8.0")
        target("*.gradle.kts")
    }
}

// =============================================================================
// Detekt 2.x (新パッケージ dev.detekt)
// =============================================================================
detekt {
    buildUponDefaultConfig.set(true)
    config.setFrom(files("config/detekt/detekt.yml"))
    parallel.set(true)
    ignoreFailures.set(false)
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        checkstyle.required.set(true)
        sarif.required.set(false)
        markdown.required.set(false)
    }
}
