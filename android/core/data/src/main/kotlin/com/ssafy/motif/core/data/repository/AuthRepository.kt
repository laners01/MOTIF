package com.ssafy.motif.core.data.repository

import kotlinx.coroutines.flow.Flow

interface AuthRepository {

    fun signInWithGoogle(
        idToken: String,
    ): Flow<Unit>
}