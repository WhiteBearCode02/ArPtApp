package com.example.arptapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arptapp.data.remote.RankingCategory
import com.example.arptapp.data.remote.YouTubeRanking
import com.example.arptapp.data.remote.YouTubeRankingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RankingUiState {
    data object Idle : RankingUiState
    data class Loading(val category: RankingCategory) : RankingUiState
    data class Content(val ranking: YouTubeRanking) : RankingUiState
    data class Error(val message: String) : RankingUiState
}

class YouTubeRankingViewModel : ViewModel() {
    private val repository = YouTubeRankingRepository()
    private val _state = MutableStateFlow<RankingUiState>(RankingUiState.Idle)
    val state: StateFlow<RankingUiState> = _state.asStateFlow()
    private var request: Job? = null

    fun load(category: RankingCategory) {
        if (request?.isActive == true) return
        request = viewModelScope.launch {
            _state.value = RankingUiState.Loading(category)
            _state.value = repository.load(category).fold(
                onSuccess = RankingUiState::Content,
                onFailure = { RankingUiState.Error(it.message ?: "순위를 불러오지 못했습니다.") }
            )
        }
    }
}
