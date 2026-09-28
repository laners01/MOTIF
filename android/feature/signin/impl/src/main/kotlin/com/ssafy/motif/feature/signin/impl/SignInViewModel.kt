package com.ssafy.motif.feature.signin.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    val uiState: StateFlow<SignInUiState>
        field = MutableStateFlow(
            SignInUiState(),
        )

    fun signInWithGoogle(
        idToken: String,
    ) {
        viewModelScope.launch {
            authRepository
                .signInWithGoogle(
                    idToken = idToken,
                )
                .asResult()
                .collect { result ->
                    when (result) {
                        Result.Loading -> {
                            uiState.value =
                                SignInUiState(
                                    isLoading = true,
                                )
                        }

                        is Result.Success -> {
                            uiState.value =
                                SignInUiState()
                        }

                        is Result.Error -> {
                            uiState.value =
                                SignInUiState(
                                    isError = true,
                                )
                        }
                    }
                }
        }
    }

    fun setSignInError() {
        uiState.value =
            SignInUiState(
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

data class SignInUiState(
    val isLoading: Boolean = false,
    val isError: Boolean = false,
)