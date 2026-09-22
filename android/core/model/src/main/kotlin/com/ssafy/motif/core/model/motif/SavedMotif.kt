package com.ssafy.motif.core.model.motif

import com.ssafy.motif.core.model.artist.Artist

/**
 * 사용자가 최종 저장한 모티프 영상. 보관함에 표시된다.
 *
 * @param description 에셋 소개 문구.
 * @param artist 함께 제작한(WITH) 아티스트.
 * @param savedAt 저장 시각 (epoch millis).
 * @param isRepresentative 대표 모티프 여부. 사용자당 하나만 true다.
 */
data class SavedMotif(
    val id: Long,
    val videoUrl: String,
    val thumbnailUrl: String,
    val description: String,
    val artist: Artist,
    val savedAt: Long,
    val isRepresentative: Boolean = false,
)
