package com.example.redmineagent.domain.model

/**
 * `sync_run.status` の値域。DB 側にも CHECK 制約で同じ値域が定義されている (V1__init.sql)。
 */
enum class SyncRunStatus {
    RUNNING,
    SUCCESS,
    FAILED,
}
