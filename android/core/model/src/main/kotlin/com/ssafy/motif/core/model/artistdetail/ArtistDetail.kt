package com.ssafy.motif.core.model.artistdetail

data class ArtistDetail(
    val name: String,
    val groupName: String,
    val profileImageUrl: String?,
    val introducing: String,
    val subscribeNum: Long,
    val challengeNum: Long,
    val isSubscribed: Boolean,
    val isAlarmOn: Boolean,
    val hasMotifPass: Boolean,
)