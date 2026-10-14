package com.example.arptapp.presentation.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arptapp.data.remote.*
import com.example.arptapp.domain.report.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Rotation-safe in-memory result only. No cross-account disk cache or automatic retries. */
class AiReportViewModel : ViewModel() {
    private val repository = AiCoachingRepository()
    private val mutableState = MutableStateFlow<AiReportState>(AiReportState.Idle)
    val state = mutableState.asStateFlow()
    fun generate(input: StructuredWorkoutAnalysis) {
        if (mutableState.value is AiReportState.Loading || mutableState.value is AiReportState.Success) return
        mutableState.value = AiReportState.Loading
        viewModelScope.launch {
            mutableState.value = repository.generateWorkoutReport(input).fold(
                onSuccess = { AiReportState.Success(it) },
                onFailure = { error -> when (error) {
                    is AiUnavailableException -> AiReportState.Unavailable("AI 설명은 현재 비활성 상태입니다. 로컬 분석을 확인해 주세요.")
                    is AiRequestException -> AiReportState.Error(when (error.status) {
                        401 -> "로그인 상태를 확인해 주세요. 로컬 분석은 계속 볼 수 있습니다."
                        429 -> "오늘 AI 설명 요청 한도를 사용했습니다. 로컬 분석을 확인해 주세요."
                        408, 504 -> "AI 설명 응답이 지연되었습니다. 로컬 분석을 확인해 주세요."
                        else -> "AI 설명을 가져오지 못했습니다. 로컬 분석은 유지됩니다."
                    })
                    else -> AiReportState.Error("AI 설명을 검증하거나 연결할 수 없습니다. 로컬 분석은 유지됩니다.")
                } }
            )
        }
    }
}
