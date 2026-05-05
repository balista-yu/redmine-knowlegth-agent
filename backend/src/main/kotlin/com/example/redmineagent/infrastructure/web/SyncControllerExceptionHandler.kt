package com.example.redmineagent.infrastructure.web

import com.example.redmineagent.application.exception.SyncAlreadyRunningException
import com.example.redmineagent.infrastructure.web.dto.ApiErrorDto
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * `SyncController` 関連の例外 → REST レスポンスのマッピング。
 *
 * `SyncAlreadyRunningException` → 409 + `code=SYNC_ALREADY_RUNNING` + `currentRunId`。
 * 他のエラー (400 / 500 等) は Spring 標準のハンドラに任せる。
 *
 * `@RestControllerAdvice` の対象を `SyncController` パッケージに絞ることで、将来の
 * `ChatController` 等が独自のエラーフォーマットを持っても干渉しないようにする。
 */
@RestControllerAdvice(basePackageClasses = [SyncController::class])
class SyncControllerExceptionHandler {
    @ExceptionHandler(SyncAlreadyRunningException::class)
    fun handleSyncAlreadyRunning(e: SyncAlreadyRunningException): ResponseEntity<ApiErrorDto> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiErrorDto(
                code = "SYNC_ALREADY_RUNNING",
                message = e.message ?: "Sync is already running",
                currentRunId = e.currentRunId,
            ),
        )
}
