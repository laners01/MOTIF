package com.ssafy.motif.core.model.challenge

data class CaptureGuideVideo(
    val participantId: Long,
    val challengeId: Long,
    val artistId: Long,
    val videoUrl: String,
    val durationMs: Long,
    val expiresIn: Long,
)