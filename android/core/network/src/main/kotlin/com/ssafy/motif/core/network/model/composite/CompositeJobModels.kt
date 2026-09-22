package com.ssafy.motif.core.network.model.composite

import kotlinx.serialization.Serializable

@Serializable
data class CreateCompositeJobRequest(
    val jobType: CompositeJobType,
    val contentLength: Long,
    val challengeSeq: Long? = null,
    val assetSeq: Long? = null,
    val challengeParticipantSeq: Long? = null,
)

@Serializable
data class CreateCompositeJobResponse(
    val jobUuid: String,
    val uploadUrl: String,
    val expiresIn: Long,
)

@Serializable
data class RequestCompositeJobRequest(
    val correctionEnabled: Boolean? = null,
)

@Serializable
data class RequestCompositeJobResponse(
    val jobUuid: String,
    val status: CompositeJobStatus,
)

@Serializable
data class CompositeJobResponse(
    val jobUuid: String,
    val jobType: CompositeJobType,
    val status: CompositeJobStatus,
    val attempt: Int,
    val failCode: String? = null,
    val resultVideoUrl: String? = null,
    val resultThumbnailUrl: String? = null,
    val resultSeq: Long? = null,
)

@Serializable
data class RetryCompositeJobResponse(
    val jobUuid: String,
    val status: CompositeJobStatus,
    val attempt: Int,
)

@Serializable
enum class CompositeJobType {
    CHALLENGE,
    MOTIF,
}

@Serializable
enum class CompositeJobStatus {
    UPLOADED,
    QUEUED,
    PROCESSING,
    DONE,
    FAILED,
}