package com.ssafy.motif.feature.signin.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<SignInUiState>
        field = MutableStateFlow(SignInUiState())

    private val signInEventChannel =
        Channel<SignInEvent>(
            capacity = Channel.BUFFERED,
        )

    val signInEvent =
        signInEventChannel.receiveAsFlow()

    fun signInWithGoogle(
        idToken: String,
    ) {
        viewModelScope.launch {
            authRepository
                .signInWithGoogle(idToken)
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading -> {
                            uiState.value = SignInUiState(
                                isLoading = true,
                            )
                        }

                        is Result.Success -> {
                            uiState.value = SignInUiState()

                            signInEventChannel.send(
                                SignInEvent.Success,
                            )
                        }

                        is Result.Error -> {
                            uiState.value = SignInUiState(
                                isError = true,
                            )
                        }
                    }
                }
        }
    }

    fun setSignInError() {
        uiState.value = SignInUiState(
            isError = true,
        )
    }

    fun clearSignInError() {
        if (uiState.value.isError) {
            uiState.value =
                uiState.value.copy(
                    isError = false,
                )
        }
    }
}

sealed interface SignInEvent {
    data object Success : SignInEvent
}

data class SignInUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)