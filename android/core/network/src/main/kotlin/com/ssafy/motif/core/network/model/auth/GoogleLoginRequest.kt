package com.ssafy.motif.core.network.model.auth

import kotlinx.serialization.Serializable

@Serializable
data class GoogleLoginRequest(
    val idToken: String,
)