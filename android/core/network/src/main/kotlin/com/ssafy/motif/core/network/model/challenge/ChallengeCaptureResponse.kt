package com.ssafy.motif.core.network.model.challenge

import kotlinx.serialization.Serializable

@Serializable
data class ChallengeCapturePositionListResponse(
    val list: List<ChallengeCapturePositionResponse>,
)

@Serializable
data class ChallengeCapturePositionResponse(
    val challengeParticipantSeq: Long,
    val artistSeq: Long,
    val name: String,
    val slotOrder: Int,
    val profileImageUrl: String? = null,
    val anchor: ChallengeCaptureAnchorResponse? = null,
    val initialSceneUrl: String? = null,
    val previewImageUrl: String? = null,
)

@Serializable
data class ChallengeCaptureAnchorResponse(
    val x: Double,
    val y: Double,
    val r: Double,
)

@Serializable
data class ChallengeCaptureVideoResponse(
    val challengeParticipantSeq: Long,
    val challengeSeq: Long,
    val artistSeq: Long,
    val videoUrl: String,
    val durationMs: Long,
    val expiresIn: Long,
)