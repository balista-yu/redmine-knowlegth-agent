package com.example.redmineagent.domain.gateway

/**
 * テキストを Embedding ベクトルに変換するサービス抽象。
 *
 * Infrastructure 層では Ollama の `/api/embed` を Spring WebClient で直接叩いて
 * 実装される (`OllamaEmbeddingService`)。Koog 0.8.0 の `LLMEmbedder` は旧
 * `/api/embeddings` エンドポイントとレスポンス DTO の不整合があり使用していない。
 *
 * 入力長が context length を超えた場合の挙動:
 *  - Infrastructure 実装は `EmbeddingTooLongException` (Application 層) を投げる
 *  - Application Service 側で半分分割再試行 (T-1-10)
 * Domain interface は例外宣言を持たない (Kotlin に checked exception が無いことに加え、
 * Domain → Application 例外への参照を作らないため)。
 *
 * 詳細仕様: docs/01-design.md §8.5
 */
interface EmbeddingService {
    /** 入力テキストを Embedding ベクトルに変換する。次元はモデル依存。 */
    suspend fun embed(text: String): FloatArray
}
