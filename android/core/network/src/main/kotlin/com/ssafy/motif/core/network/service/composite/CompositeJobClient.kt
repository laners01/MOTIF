package com.ssafy.motif.core.network.service.composite

import com.ssafy.motif.core.network.di.UploadHttpClient
import com.ssafy.motif.core.network.model.composite.CompositeJobResponse
import com.ssafy.motif.core.network.model.composite.CompositeJobType
import com.ssafy.motif.core.network.model.composite.CreateCompositeJobRequest
import com.ssafy.motif.core.network.model.composite.CreateCompositeJobResponse
import com.ssafy.motif.core.network.model.composite.RequestCompositeJobRequest
import com.ssafy.motif.core.network.model.composite.RequestCompositeJobResponse
import com.ssafy.motif.core.network.model.composite.RetryCompositeJobResponse
import java.io.File
import java.io.IOException
import javax.inject.Inject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

class CompositeJobClient @Inject constructor(
    private val compositeJobService: CompositeJobService,
    @param:UploadHttpClient
    private val uploadHttpClient: OkHttpClient,
) {

    suspend fun createChallengeJob(
        challengeId: Long,
        challengeParticipantId: Long? = null,
        contentLength: Long,
    ): CreateCompositeJobResponse =
        compositeJobService
            .createCompositeJob(
                request = CreateCompositeJobRequest(
                    jobType = CompositeJobType.CHALLENGE,
                    challengeSeq = challengeId,
                    challengeParticipantSeq =
                        challengeParticipantId,
                    contentLength = contentLength,
                ),
            )
            .data

    suspend fun uploadVideo(
        uploadUrl: String,
        videoFile: File,
    ) {
        require(videoFile.exists()) {
            "Video file does not exist."
        }

        require(videoFile.isFile) {
            "Video path is not a file."
        }

        require(videoFile.length() > 0L) {
            "Video file is empty."
        }

        val requestBody =
            videoFile.asRequestBody(
                VIDEO_MP4_MEDIA_TYPE,
            )

        val request =
            Request
                .Builder()
                .url(uploadUrl)
                .put(requestBody)
                .header(
                    CONTENT_TYPE_HEADER,
                    VIDEO_MP4,
                )
                .header(
                    CONTENT_LENGTH_HEADER,
                    videoFile.length()
                        .toString(),
                )
                .build()

        uploadHttpClient
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException(
                        "Video upload failed. " +
                                "HTTP ${response.code}",
                    )
                }
            }
    }

    suspend fun requestChallengeJob(
        jobUuid: String,
        correctionEnabled: Boolean,
    ): RequestCompositeJobResponse =
        compositeJobService
            .requestCompositeJob(
                jobUuid = jobUuid,
                request =
                    RequestCompositeJobRequest(
                        correctionEnabled =
                            correctionEnabled,
                    ),
            )
            .data

    suspend fun getCompositeJob(
        jobUuid: String,
    ): CompositeJobResponse =
        compositeJobService
            .getCompositeJob(
                jobUuid = jobUuid,
            )
            .data

    suspend fun retryCompositeJob(
        jobUuid: String,
    ): RetryCompositeJobResponse =
        compositeJobService
            .retryCompositeJob(
                jobUuid = jobUuid,
            )
            .data

    private companion object {
        const val VIDEO_MP4 = "video/mp4"

        const val CONTENT_TYPE_HEADER =
            "Content-Type"

        const val CONTENT_LENGTH_HEADER =
            "Content-Length"

        val VIDEO_MP4_MEDIA_TYPE =
            VIDEO_MP4.toMediaType()
    }
}