package com.ssafy.motif.core.data.mapper

import com.ssafy.motif.core.model.auth.AuthLoginResult
import com.ssafy.motif.core.network.model.auth.AuthResponse

internal fun AuthResponse.toModel(): AuthLoginResult =
    AuthLoginResult(
        userId = user.userSeq,
        isNewUser = isNewUser,
    )