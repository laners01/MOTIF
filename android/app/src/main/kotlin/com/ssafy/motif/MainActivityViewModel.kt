package com.ssafy.motif

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.launchIn

@HiltViewModel
internal class MainActivityViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    val myProfileImageUrl: StateFlow<String?> =
        userRepository
            .currentUserProfile
            .map { profile ->
                profile?.profileImageUrl
            }
            .stateIn(
                scope = viewModelScope,
                started =
                    SharingStarted.WhileSubscribed(
                        stopTimeoutMillis = 5_000,
                    ),
                initialValue = null,
            )

    private var profileLoadJob: Job? = null

    fun loadUserProfile() {
        profileLoadJob?.cancel()

        profileLoadJob =
            userRepository
                .getUserOverview()
                .asResult()
                .launchIn(
                    viewModelScope,
                )
    }

    fun clearUserProfile() {
        profileLoadJob?.cancel()
        profileLoadJob = null

        userRepository
            .clearCurrentUserProfile()
    }
}