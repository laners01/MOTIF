package com.ssafy.motif.core.model.auth

data class AuthLoginResult(
    val userId: Long,
    val isNewUser: Boolean,
)