package com.example.arptapp.presentation.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.arptapp.data.history.WorkoutHistoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.time.Instant

sealed interface WorkoutSaveState {
    data object Idle : WorkoutSaveState
    data object Saving : WorkoutSaveState
    data object Saved : WorkoutSaveState
    data class Error(val message: String) : WorkoutSaveState
}

/**
 * ViewModel + SavedStateHandle retain one UUID across rotation/retry/process restoration.
 * Room's unique session index closes the commit-before-state-save duplication window.
 */
class WorkoutResultViewModel(
    application: Application,
    private val savedState: SavedStateHandle
) : AndroidViewModel(application) {
    private val saveGate = WorkoutResultSaveGate(savedState[STATE_SAVED] ?: false)
    private val _saveState = MutableStateFlow<WorkoutSaveState>(
        if (saveGate.state == WorkoutResultSaveGate.State.COMPLETED) WorkoutSaveState.Saved else WorkoutSaveState.Idle
    )
    val saveState: StateFlow<WorkoutSaveState> = _saveState.asStateFlow()
    private var resolvedReport: WorkoutReportData? = null

    fun resolveReport(incoming: WorkoutReportData): WorkoutReportData = resolvedReport ?: run {
        val date = savedState.get<String>(STATE_DATE) ?: SimpleDateFormat(
            "yyyy-MM-dd HH:mm", Locale.KOREA
        ).format(Date()).also { savedState[STATE_DATE] = it }
        val id = incoming.sessionId ?: savedState.get<String>(STATE_SESSION)
            ?: UUID.randomUUID().toString().also { savedState[STATE_SESSION] = it }
        val created = incoming.createdAt.ifBlank {
            savedState.get<String>(STATE_CREATED) ?: Instant.now().toString().also { savedState[STATE_CREATED] = it }
        }
        val owner = savedState.get<String>(STATE_OWNER) ?: incoming.ownerUserId.ifBlank {
            WorkoutHistoryProvider.accounts.current()?.userId.orEmpty()
        }.also { savedState[STATE_OWNER] = it }
        incoming.completedAt(date).forSaving(id, owner, created).also { resolvedReport = it }
    }

    fun save(report: WorkoutReportData, userId: String) {
        if (saveGate.state != WorkoutResultSaveGate.State.READY) return
        val repository = WorkoutHistoryProvider.get(getApplication<Application>())
        val account = repository.currentAccount()
        if (userId.isBlank() || account?.userId != userId || report.ownerUserId != userId) {
            _saveState.value = WorkoutSaveState.Error("로그인 정보를 확인할 수 없어 운동 기록을 저장하지 못했습니다.")
            return
        }
        val ownerId = savedState.get<String>(STATE_OWNER)
        if (ownerId != null && ownerId != userId) {
            _saveState.value = WorkoutSaveState.Error("계정이 변경되어 이전 운동 기록을 저장할 수 없습니다.")
            return
        }
        // 실패 뒤 화면 회전/재시도에서도 원래 계정 소유권을 유지한다.
        savedState[STATE_OWNER] = userId
        if (!saveGate.begin()) return
        _saveState.value = WorkoutSaveState.Saving
        viewModelScope.launch {
            try {
                repository.save(requireNotNull(account), report.toRecord(userId)) {
                    saveGate.complete()
                    savedState[STATE_SAVED] = true
                    _saveState.value = WorkoutSaveState.Saved
                }
            } catch (cancelled: CancellationException) {
                // 취소는 저장 오류/재시도 UI로 변환하지 않고 소유한 코루틴에 전달한다.
                throw cancelled
            } catch (_: Exception) {
                if (saveGate.state != WorkoutResultSaveGate.State.COMPLETED)
                    _saveState.value = WorkoutSaveState.Error("운동 기록을 저장하지 못했습니다. 다시 시도해 주세요.")
            } finally {
                saveGate.failed()
            }
        }
    }

    private companion object {
        const val STATE_SAVED = "workout_result_saved"
        const val STATE_DATE = "workout_result_date"
        const val STATE_OWNER = "workout_result_owner"
        const val STATE_SESSION = "workout_result_session_id"
        const val STATE_CREATED = "workout_result_created_at"
    }
}
