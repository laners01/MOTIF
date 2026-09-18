package com.ssafy.motif.core.network.auth

interface AccessTokenProvider {

    fun getAccessToken(): String?
}