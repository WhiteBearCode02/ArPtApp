package com.example.arptapp.presentation.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.arptapp.data.AppDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed interface WorkoutSaveState {
    data object Idle : WorkoutSaveState
    data object Saving : WorkoutSaveState
    data object Saved : WorkoutSaveState
    data class Error(val message: String) : WorkoutSaveState
}

/**
 * Room 저장 수명은 Activity가 아닌 ViewModel이 소유하므로 화면 회전에 의해 중복 insert하지 않는다.
 * SavedStateHandle은 완료 여부/최초 완료 일시를 복원한다. 기존 DB에는 영속 세션 키가 없으므로
 * DB commit 직후 프로세스가 종료된 경우까지 원자적으로 중복을 막는 구조는 아니다.
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
        incoming.completedAt(date).also { resolvedReport = it }
    }

    fun save(report: WorkoutReportData, userId: String) {
        if (saveGate.state != WorkoutResultSaveGate.State.READY) return
        if (userId.isBlank()) {
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
                AppDatabase.getDatabase(getApplication<Application>()).exerciseDao().insertRecord(report.toRecord(userId))
                saveGate.complete()
                savedState[STATE_SAVED] = true
                _saveState.value = WorkoutSaveState.Saved
            } catch (cancelled: CancellationException) {
                // 취소는 저장 오류/재시도 UI로 변환하지 않고 소유한 코루틴에 전달한다.
                throw cancelled
            } catch (_: Exception) {
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
    }
}
