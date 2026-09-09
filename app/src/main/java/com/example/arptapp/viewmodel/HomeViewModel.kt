package com.example.arptapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.arptapp.data.remote.WorkoutTrendSnapshot
import com.example.arptapp.data.remote.YouTubeTrendRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WorkoutTrendsUiState {
    data object Loading : WorkoutTrendsUiState
    data class Content(val snapshot: WorkoutTrendSnapshot) : WorkoutTrendsUiState
    data class Unavailable(val message: String) : WorkoutTrendsUiState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = YouTubeTrendRepository(application)
    private val _trends = MutableStateFlow<WorkoutTrendsUiState>(WorkoutTrendsUiState.Loading)
    val trends: StateFlow<WorkoutTrendsUiState> = _trends.asStateFlow()

    fun loadTrends() {
        viewModelScope.launch {
            _trends.value = WorkoutTrendsUiState.Loading
            _trends.value = repository.load().fold(
                onSuccess = WorkoutTrendsUiState::Content,
                onFailure = { WorkoutTrendsUiState.Unavailable(it.message ?: "트렌드 정보를 불러오지 못했습니다.") }
            )
        }
    }
}
