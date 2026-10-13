package com.example.arptapp.presentation.report

/**
 * 메인 스레드에서 사용하는 단일 결과 화면 저장 게이트. 회전 중 재진입과 저장 완료 후 재저장을 막는다.
 * 프로세스 강제 종료와 DB commit 사이의 정확히 한 번 보장은 영속 세션 키/DB UNIQUE 제약이 별도로 필요하다.
 */
class WorkoutResultSaveGate(completed: Boolean = false) {
    enum class State { READY, SAVING, COMPLETED }

    var state: State = if (completed) State.COMPLETED else State.READY
        private set

    fun begin(): Boolean {
        if (state != State.READY) return false
        state = State.SAVING
        return true
    }

    fun complete() {
        check(state == State.SAVING)
        state = State.COMPLETED
    }

    fun failed() {
        if (state == State.SAVING) state = State.READY
    }
}
