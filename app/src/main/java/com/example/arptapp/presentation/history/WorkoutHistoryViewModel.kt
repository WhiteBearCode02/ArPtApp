package com.example.arptapp.presentation.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.arptapp.data.ExerciseRecord
import com.example.arptapp.data.history.*
import com.example.arptapp.data.remote.AuthSessionStore
import com.example.arptapp.presentation.report.WorkoutReportData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class WorkoutHistoryState(val account: WorkoutAccount? = null,
    val records: List<ExerciseRecord> = emptyList(), val syncing: Boolean = false,
    val message: String = "로그인이 필요합니다.")

/** History and statistics share the same account-fenced, cache-first loading policy. */
class WorkoutHistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WorkoutHistoryProvider.get(application)
    private val mutableState = MutableStateFlow(WorkoutHistoryState())
    val state = mutableState.asStateFlow()
    private val mutableReport = MutableStateFlow<WorkoutReportData?>(null)
    val report = mutableReport.asStateFlow()
    private var refreshJob: Job? = null
    private var detailsJob: Job? = null

    init {
        viewModelScope.launch {
            AuthSessionStore.workoutAccount.collect { account ->
                refreshJob?.cancel(); detailsJob?.cancel()
                mutableReport.value = null
                mutableState.value = WorkoutHistoryState(account = account)
                if (account != null) refresh(false)
            }
        }
    }

    fun refresh(force: Boolean = true) {
        val account = repository.currentAccount() ?: return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val cached = repository.cached(account)
                if (!repository.isCurrent(account)) return@launch
                mutableState.value = WorkoutHistoryState(account, cached, true, "계정의 운동 기록을 동기화하고 있습니다.")
                val outcome = repository.syncCurrentUser(force)
                val records = repository.cached(account)
                if (!repository.isCurrent(account)) return@launch
                val message = when (outcome) {
                    SyncOutcome.FAILED -> "동기화하지 못했습니다. 기기에 저장된 기록은 그대로 볼 수 있습니다."
                    SyncOutcome.PARTIAL -> "일부 기록을 반영했습니다. 더 불러오려면 다시 동기화해 주세요."
                    else -> if (records.any { it.syncState in listOf("PENDING", "FAILED") })
                        "서버 저장을 기다리는 기록이 있습니다. 다시 동기화해 주세요." else "기기에 저장된 기록을 표시합니다."
                }
                mutableState.value = WorkoutHistoryState(account, records, false, message)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (repository.isCurrent(account)) mutableState.value = mutableState.value.copy(syncing = false,
                    message = "기록을 불러오지 못했습니다. 다시 시도해 주세요.")
            }
        }
    }

    fun open(record: ExerciseRecord) {
        val account = repository.currentAccount() ?: return
        if (record.userId != account.userId || detailsJob?.isActive == true) return
        detailsJob = viewModelScope.launch {
            try {
                val detail = repository.detailed(account, record)
                if (repository.isCurrent(account)) mutableReport.value = WorkoutReportData.fromRecord(detail)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (repository.isCurrent(account)) mutableState.value = mutableState.value.copy(
                    message = "상세 내용을 아직 받지 못했습니다. 네트워크 확인 후 이 기록을 다시 눌러 주세요.")
            }
        }
    }
    fun reportOpened() { mutableReport.value = null }
}
