package com.ssafy.motif.core.network.service.user

import com.ssafy.motif.core.network.di.UploadHttpClient
import com.ssafy.motif.core.network.model.user.ProfileImagePresignRequest
import com.ssafy.motif.core.network.model.user.ProfileImagePresignResponse
import com.ssafy.motif.core.network.model.user.UpdateNicknameRequest
import com.ssafy.motif.core.network.model.user.UpdateProfileImageRequest
import com.ssafy.motif.core.network.model.user.UserActivityListResponse
import com.ssafy.motif.core.network.model.user.UserOverviewResponse
import com.ssafy.motif.core.network.model.user.UserProfileResponse
import com.ssafy.motif.core.network.model.user.UserSubscriptionListResponse
import com.ssafy.motif.core.network.model.user.UserVideoListResponse
import java.io.IOException
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class UserClient @Inject constructor(
    private val userService: UserService,
    @param:UploadHttpClient
    private val uploadHttpClient: OkHttpClient,
) {

    suspend fun getMe(): UserOverviewResponse =
        userService
            .getMe()
            .data

    suspend fun updateNickname(
        nickname: String,
    ): String =
        userService
            .updateNickname(
                request =
                    UpdateNicknameRequest(
                        nickname = nickname,
                    ),
            )
            .data
            .nickname

    suspend fun createProfileImagePresign(
        contentType: String,
        contentLength: Long,
    ): ProfileImagePresignResponse =
        userService
            .createProfileImagePresign(
                request =
                    ProfileImagePresignRequest(
                        contentType = contentType,
                        contentLength = contentLength,
                    ),
            )
            .data

    suspend fun uploadProfileImage(
        uploadUrl: String,
        contentType: String,
        imageBytes: ByteArray,
    ) {
        require(imageBytes.isNotEmpty()) {
            "Profile image is empty."
        }

        require(imageBytes.size.toLong() <= MAX_PROFILE_IMAGE_BYTES) {
            "Profile image exceeds the maximum size."
        }

        require(contentType in ALLOWED_IMAGE_CONTENT_TYPES) {
            "Unsupported profile image content type: $contentType"
        }

        val requestBody =
            imageBytes.toRequestBody(
                contentType.toMediaType(),
            )

        val request =
            Request
                .Builder()
                .url(uploadUrl)
                .put(requestBody)
                .header(
                    CONTENT_TYPE_HEADER,
                    contentType,
                )
                .header(
                    CONTENT_LENGTH_HEADER,
                    imageBytes.size.toString(),
                )
                .build()

        uploadHttpClient
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "Profile image upload failed. " +
                                "HTTP ${response.code}",
                    )
                }
            }
    }

    suspend fun updateProfileImage(
        imageKey: String,
    ): String =
        userService
            .updateProfileImage(
                request =
                    UpdateProfileImageRequest(
                        imageKey = imageKey,
                    ),
            )
            .data
            .profileImageUrl

    suspend fun getUserProfile(
        id: Long,
    ): UserProfileResponse =
        userService
            .getUserProfile(
                userSeq = id,
            )
            .data

    suspend fun getSubscriptions(): UserSubscriptionListResponse =
        userService
            .getSubscriptions()
            .data

    suspend fun getVideos(): UserVideoListResponse =
        userService
            .getVideos()
            .data

    suspend fun getActivities(
        cursor: String? = null,
        size: Int = DEFAULT_ACTIVITY_SIZE,
    ): UserActivityListResponse =
        userService
            .getActivities(
                cursor = cursor,
                size = size,
            )
            .data

    private companion object {

        const val DEFAULT_ACTIVITY_SIZE = 20

        const val MAX_PROFILE_IMAGE_BYTES =
            10L * 1024L * 1024L

        const val CONTENT_TYPE_HEADER =
            "Content-Type"

        const val CONTENT_LENGTH_HEADER =
            "Content-Length"

        val ALLOWED_IMAGE_CONTENT_TYPES =
            setOf(
                "image/jpeg",
                "image/png",
                "image/webp",
            )
    }
}