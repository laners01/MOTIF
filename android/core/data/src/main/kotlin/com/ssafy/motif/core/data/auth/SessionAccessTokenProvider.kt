package com.ssafy.motif.core.data.auth

import com.ssafy.motif.core.network.auth.AccessTokenProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class SessionAccessTokenProvider @Inject constructor() :
    AccessTokenProvider {

    @Volatile
    private var accessToken: String? = null

    override fun getAccessToken(): String? =
        accessToken

    fun updateAccessToken(
        accessToken: String?,
    ) {
        this.accessToken =
            accessToken
                ?.takeIf(String::isNotBlank)
    }

    fun clearAccessToken() {
        accessToken = null
    }
}