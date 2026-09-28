package com.ssafy.motif.core.model.composite

data class CompositeJobUpload(
    val jobUuid: String,
    val uploadUrl: String,
    val expiresIn: Long,
)

data class CompositeJobRequestResult(
    val jobUuid: String,
    val status: CompositeJobStatus,
)

data class ChallengeCompositeJob(
    val jobUuid: String,
    val status: CompositeJobStatus,
    val attempt: Int,
    val failCode: String?,
    val resultVideoUrl: String?,
    val resultThumbnailUrl: String?,
)

data class CompositeJobRetryResult(
    val jobUuid: String,
    val status: CompositeJobStatus,
    val attempt: Int,
)

enum class CompositeJobStatus {
    UPLOADED,
    QUEUED,
    PROCESSING,
    DONE,
    FAILED,
}