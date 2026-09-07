package com.example.arptapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.arptapp.data.remote.AppSession
import com.example.arptapp.data.remote.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LoginState {
    data object Idle : LoginState
    data object Loading : LoginState
    data class Authenticated(val session: AppSession) : LoginState
    data class SignedUp(val needsEmailConfirmation: Boolean) : LoginState
    data class Error(val message: String) : LoginState
}

class LoginViewModel : ViewModel() {
    private val repository = SupabaseAuthRepository()
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state: StateFlow<LoginState> = _state.asStateFlow()

    fun signIn(email: String, password: String) {
        launchAuth {
            repository.signIn(email, password).fold(
                onSuccess = { _state.value = LoginState.Authenticated(it) },
                onFailure = { _state.value = LoginState.Error(it.message ?: "로그인에 실패했습니다.") }
            )
        }
    }

    fun signUp(email: String, password: String) {
        launchAuth {
            repository.signUp(email, password).fold(
                onSuccess = { session ->
                    _state.value = if (session == null) {
                        LoginState.SignedUp(needsEmailConfirmation = true)
                    } else {
                        LoginState.Authenticated(session)
                    }
                },
                onFailure = { _state.value = LoginState.Error(it.message ?: "회원가입에 실패했습니다.") }
            )
        }
    }

    fun signInWithGoogle() {
        launchAuth {
            repository.startGoogleSignIn().onFailure {
                _state.value = LoginState.Error(it.message ?: "Google 로그인에 실패했습니다.")
            }
        }
    }

    fun handleAuthCallback(intent: android.content.Intent) {
        launchAuth {
            repository.handleAuthCallback(intent).fold(
                onSuccess = { session ->
                    if (session == null) {
                        _state.value = LoginState.Error("인증 세션을 확인할 수 없습니다.")
                    } else {
                        _state.value = LoginState.Authenticated(session)
                    }
                },
                onFailure = { _state.value = LoginState.Error(it.message ?: "OAuth 인증에 실패했습니다.") }
            )
        }
    }

    private fun launchAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            block()
        }
    }
}
