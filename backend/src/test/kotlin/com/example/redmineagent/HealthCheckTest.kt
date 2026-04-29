package com.example.redmineagent

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.web.reactive.function.client.WebClient

/**
 * Spring Boot Actuator の /actuator/health が UP を返すことを確認するスモークテスト。
 *
 * T-1-1 の DoD: `./gradlew test` でこのテストが通ること。
 *
 * Note: DataSource / JPA / Flyway は AutoConfiguration を exclude して DB なしで起動。
 * 永続化系の統合テストは T-1-9 で Testcontainers + postgres を使う。
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        // Spring Boot 4.0 で autoconfig の置き場が モジュール別に再編されたため新パスを指定
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    ],
)
class HealthCheckTest {
    @LocalServerPort
    private var port: Int = 0

    @Test
    fun `actuator health endpoint returns UP`() {
        val client = WebClient.create("http://localhost:$port")

        val body =
            client
                .get()
                .uri("/actuator/health")
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

        body?.get("status") shouldBe "UP"
    }
}
