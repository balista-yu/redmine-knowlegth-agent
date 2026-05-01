package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.core.spec.IsolationMode

/**
 * Kotest プロジェクト全体の設定。Kotest が起動時に自動探索する FQN
 * (`io.kotest.provided.ProjectConfig`) に置く必要がある。
 *
 * - `isolationMode = InstancePerTest`: 各 test ごとに spec インスタンスを作り直す。
 *   JUnit と同じ「テストごとにフィールド (mockk 等) が新規」セマンティクスとなり、
 *   テスト間の状態リークを防ぐ。
 */
object ProjectConfig : AbstractProjectConfig() {
    override val isolationMode: IsolationMode = IsolationMode.InstancePerTest
}
