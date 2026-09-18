package com.ssafy.motif.core.data.auth

import com.ssafy.motif.core.network.auth.AccessTokenProvider
import javax.inject.Inject

internal class SessionAccessTokenProvider @Inject constructor() : AccessTokenProvider {

    override fun getAccessToken(): String =
        ACCESS_TOKEN

    private companion object {
        const val ACCESS_TOKEN =
            "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwicm9sZSI6IlVTRVIiLCJpYXQiOjE3ODk1MzMzNTgsImV4cCI6MTc5MjEyNTM1OH0.7gsFNKjdicSPTpv_iRyWCf45OSjS1foJGwMRF_9ZrjeGAy3iWlERCMM9ugIqTZv1sVGSgZsNKwqJtMv_G1ABIw"
    }
}