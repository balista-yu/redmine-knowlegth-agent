package com.example.redmineagent.domain.gateway

import com.example.redmineagent.domain.model.IssuePage
import java.time.Instant

/**
 * Redmine REST API への問い合わせ抽象。
 *
 * Infrastructure 層では Spring `WebClient` + `X-Redmine-API-Key` ヘッダで実装される
 * (`RedmineHttpGateway`)。Domain は HTTP / DTO の存在を知らない。
 *
 * 詳細仕様: docs/01-design.md §3.2.1 (F-01), §3.2.2 (F-03)
 */
interface RedmineGateway {
    /**
     * 指定時刻以降に更新されたチケットを 1 ページ取得する。
     *
     * @param since `null` の場合は時刻フィルタなし (初回フルロード相当)
     * @param offset ページング開始位置 (0 起点)
     * @param limit 1 ページあたりの最大件数
     */
    suspend fun listIssuesUpdatedSince(
        since: Instant?,
        offset: Int,
        limit: Int,
    ): IssuePage

    /**
     * 全チケット ID を取得する (Reconcile 用)。
     * Redmine 側で削除されたチケットを Qdrant 側と突合するために使用。
     */
    suspend fun listAllIssueIds(): Set<Int>
}
