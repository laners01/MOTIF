package com.ssafy.motif.core.model.auth

sealed interface AuthState {

    data object Initializing : AuthState

    data object SignedOut : AuthState

    data object OnboardingRequired : AuthState

    data object SignedIn : AuthState
}