package com.example.redmineagent.infrastructure.config

import io.qdrant.client.QdrantClient
import io.qdrant.client.QdrantGrpcClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Qdrant gRPC クライアントの Bean 配線。
 *
 * `app.qdrant.host` / `app.qdrant.port` を `application.yml` から注入。
 * ホスト不在 (env 未設定) はそもそも backend が動かない前提なので、ここで失敗しても良い。
 */
@Configuration
class QdrantConfig {
    @Bean(destroyMethod = "close")
    fun qdrantClient(
        @Value("\${app.qdrant.host}") host: String,
        @Value("\${app.qdrant.port}") port: Int,
    ): QdrantClient {
        // useTls=false (内部通信)。本番運用時は TLS 化を検討
        val grpc = QdrantGrpcClient.newBuilder(host, port, false).build()
        return QdrantClient(grpc)
    }
}
