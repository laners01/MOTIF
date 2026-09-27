package com.ssafy.motif.core.network.service.notification

import com.ssafy.motif.core.network.model.BaseResponse
import com.ssafy.motif.core.network.model.notification.DeactivateDeviceTokenRequest
import com.ssafy.motif.core.network.model.notification.RegisterDeviceTokenRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.HTTP
import retrofit2.http.POST

interface NotificationService {

    @POST("/api/v1/notifications/device-tokens")
    suspend fun registerDeviceToken(
        @Body request: RegisterDeviceTokenRequest,
    ): BaseResponse<JsonElement>

    @HTTP(
        method = "DELETE",
        path = "/api/v1/notifications/device-tokens",
        hasBody = true,
    )
    suspend fun deactivateDeviceToken(
        @Body request: DeactivateDeviceTokenRequest,
    ): BaseResponse<JsonElement>
}