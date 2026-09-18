package com.ssafy.motif.core.network.auth

import com.ssafy.motif.core.network.NetworkConfig
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

internal class AccessTokenInterceptor @Inject constructor(
    private val accessTokenProvider: AccessTokenProvider,
) : Interceptor {

    override fun intercept(
        chain: Interceptor.Chain,
    ): Response {
        val request = chain.request()
        val accessToken = accessTokenProvider.getAccessToken()

        if (
            accessToken.isNullOrBlank() ||
            request.url.host != NetworkConfig.API_HOST
        ) {
            return chain.proceed(request)
        }

        val authenticatedRequest =
            request
                .newBuilder()
                .header(
                    AUTHORIZATION_HEADER,
                    "$BEARER_PREFIX$accessToken",
                )
                .build()

        return chain.proceed(authenticatedRequest)
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}