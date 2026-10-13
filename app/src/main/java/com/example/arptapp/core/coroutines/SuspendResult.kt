package com.example.arptapp.core.coroutines

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 일시 중단 작업의 실패만 Result로 변환하는 공통 경계입니다.
 *
 * 화면 종료/로그아웃에 의한 취소는 오류 UI나 다음 저장 단계로 이어지면 안 됩니다.
 * runCatching과 달리 CancellationException을 다시 던지고, 취소된 작업은 시작하지 않습니다.
 * 재시도·로깅·네트워크 호출은 수행하지 않습니다.
 */
suspend fun <T> suspendResult(block: suspend () -> T): Result<T> {
    currentCoroutineContext().ensureActive()
    return try {
        val value = block()
        currentCoroutineContext().ensureActive()
        Result.success(value)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        // 취소와 I/O 실패가 동시에 발생한 경우에도 취소를 우선하여 후속 저장을 막습니다.
        currentCoroutineContext().ensureActive()
        Result.failure(error)
    }
}
