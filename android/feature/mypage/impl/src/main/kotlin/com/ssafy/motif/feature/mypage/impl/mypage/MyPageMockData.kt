package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.challenge.ParticipationVisibility
import com.ssafy.motif.core.model.user.RepresentativeMotif
import com.ssafy.motif.core.model.user.UserActivityTargetType
import com.ssafy.motif.core.model.user.UserProfile
import com.ssafy.motif.core.model.user.UserSubscription
import com.ssafy.motif.core.model.user.UserVideo

internal object MyPageMockData {

    val profile =
        UserProfile(
            id = 1L,
            nickname = "midzy_01",
            profileImageUrl = null,
        )

    const val PROFILE_IMAGE =
        "android.resource://com.ssafy.motif/drawable/profile_square"

    private val CHALLENGE_VIEWS =
        listOf(
            820_000L,
            143_000L,
            28_400L,
            9_700L,
            2_300L,
            640L,
            88L,
            12L,
        )

    val challenges: List<UserVideo> =
        List(23) { index ->
            UserVideo(
                id = index + 1L,
                thumbnailUrl =
                    "android.resource://com.ssafy.motif/drawable/ch_${index % 5 + 1}",
                viewCount =
                    CHALLENGE_VIEWS[
                        index %
                                CHALLENGE_VIEWS.size
                    ],
                visibility =
                    if (index % 4 == 0) {
                        ParticipationVisibility.PRIVATE
                    } else {
                        ParticipationVisibility.PUBLIC
                    },
            )
        }

    val artistSubscriptions: List<UserSubscription> =
        listOf(
            UserSubscription(
                artist =
                    Artist(
                        id = 1L,
                        name = "KARINA",
                        groupName = "aespa",
                        profileImageUrl =
                            "android.resource://com.ssafy.motif/drawable/yeji",
                    ),
                passActive = true,
                passExpiresAt =
                    System.currentTimeMillis() +
                            30L * ONE_DAY_MILLIS,
                subscribedDays = 421,
                challengeCount = 8L,
                purchasedAssetCount = 12L,
                totalAssetCount = 28L,
            ),
            UserSubscription(
                artist =
                    Artist(
                        id = 2L,
                        name = "Ryujin",
                        groupName = "ITZY",
                        profileImageUrl =
                            PROFILE_IMAGE,
                    ),
                passActive = false,
                passExpiresAt = null,
                subscribedDays = 42,
                challengeCount = 0L,
                purchasedAssetCount = 0L,
                totalAssetCount = 15L,
            ),
            UserSubscription(
                artist =
                    Artist(
                        id = 3L,
                        name = "Lia",
                        groupName = "ITZY",
                        profileImageUrl =
                            PROFILE_IMAGE,
                    ),
                passActive = true,
                passExpiresAt =
                    System.currentTimeMillis() +
                            14L * ONE_DAY_MILLIS,
                subscribedDays = 128,
                challengeCount = 5L,
                purchasedAssetCount = 7L,
                totalAssetCount = 7L,
            ),
            UserSubscription(
                artist =
                    Artist(
                        id = 4L,
                        name = "Chaeryeong",
                        groupName = "ITZY",
                        profileImageUrl = "",
                    ),
                passActive = false,
                passExpiresAt = null,
                subscribedDays = 7,
                challengeCount = 2L,
                purchasedAssetCount = 1L,
                totalAssetCount = 9L,
            ),
            UserSubscription(
                artist =
                    Artist(
                        id = 5L,
                        name = "Yuna",
                        groupName = "ITZY",
                        profileImageUrl =
                            PROFILE_IMAGE,
                    ),
                passActive = true,
                passExpiresAt =
                    System.currentTimeMillis() +
                            60L * ONE_DAY_MILLIS,
                subscribedDays = 1_024,
                challengeCount = 37L,
                purchasedAssetCount = 23L,
                totalAssetCount = 31L,
            ),
        )

    val representativeMotif =
        RepresentativeMotif(
            id = 1L,
            videoUrl =
                "android.resource://com.ssafy.motif/raw/test_video",
            thumbnailUrl =
                "android.resource://com.ssafy.motif/drawable/motif_thumbnail",
        )

    private val LIKE_COUNTS =
        listOf(
            1_240_000L,
            12_500L,
            1_000L,
            999L,
            128L,
            0L,
            3_400_000L,
            87L,
            1_100L,
        )

    private val NOW =
        System.currentTimeMillis()

    val likedChallenges:
            List<LikedChallengeUiModel> =
        List(17) { index ->
            LikedChallengeUiModel(
                id = index + 1L,
                targetType =
                    if (index % 3 == 0) {
                        UserActivityTargetType.CHALLENGE
                    } else {
                        UserActivityTargetType.VIDEO
                    },
                thumbnailUrl =
                    "android.resource://com.ssafy.motif/drawable/ch_${index % 5 + 1}",
                likeCount =
                    LIKE_COUNTS[
                        index %
                                LIKE_COUNTS.size
                    ],
                commentState =
                    commentStateOf(
                        index = index,
                    ),
            )
        }

    private fun commentStateOf(
        index: Int,
    ): LikedChallengeCommentUiState =
        when (index) {
            0 -> {
                LikedChallengeCommentUiState.Written(
                    myComment =
                        MyCommentUiModel(
                            content =
                                "이 챌린지 예지랑 너무 잘 어울린다🔥",
                            createdAt =
                                NOW -
                                        5L * ONE_DAY_MILLIS,
                            likeCount = 742L,
                            replyCount = 8L,
                            replies =
                                listOf(
                                    MyReplyUiModel(
                                        nickname =
                                            "dancing_mandu",
                                        profileImageUrl =
                                            REPLY_PROFILE_A,
                                        content =
                                            "😂😂😂",
                                        createdAt =
                                            NOW -
                                                    5L * ONE_DAY_MILLIS,
                                    ),
                                    MyReplyUiModel(
                                        nickname =
                                            "kkonge",
                                        profileImageUrl =
                                            PROFILE_IMAGE,
                                        content =
                                            "이거 보고 바로 챌린지 찍으러 갑니다",
                                        createdAt =
                                            NOW -
                                                    5L * ONE_DAY_MILLIS +
                                                    ONE_HOUR_MILLIS,
                                    ),
                                ),
                        ),
                )
            }

            1 -> {
                LikedChallengeCommentUiState.Written(
                    myComment =
                        MyCommentUiModel(
                            content =
                                "이 동작 진짜 어렵던데 다들 잘하시네요",
                            createdAt =
                                NOW -
                                        3L *
                                        ONE_HOUR_MILLIS,
                            likeCount =
                                12_500L,
                            replyCount =
                                2L,
                            replies =
                                listOf(
                                    MyReplyUiModel(
                                        nickname =
                                            "motif_lover",
                                        profileImageUrl =
                                            REPLY_PROFILE_A,
                                        content =
                                            "저도 삼일 걸렸어요",
                                        createdAt =
                                            NOW -
                                                    42L *
                                                    ONE_MINUTE_MILLIS,
                                    ),
                                    MyReplyUiModel(
                                        nickname =
                                            "sunny",
                                        profileImageUrl =
                                            null,
                                        content =
                                            "프로필 이미지가 없는 경우",
                                        createdAt =
                                            NOW -
                                                    30_000L,
                                    ),
                                ),
                        ),
                )
            }

            2 -> {
                LikedChallengeCommentUiState.Written(
                    myComment =
                        MyCommentUiModel(
                            content =
                                "여기 배경 어디인가요?",
                            createdAt =
                                NOW -
                                        20L *
                                        ONE_DAY_MILLIS,
                            likeCount =
                                0L,
                            replyCount =
                                0L,
                            replies =
                                emptyList(),
                        ),
                )
            }

            3 -> {
                LikedChallengeCommentUiState.Written(
                    myComment =
                        MyCommentUiModel(
                            content =
                                "이번 챌린지 음악 선정이 정말 좋았고 안무도 따라하기 쉬워서 처음 도전하는 사람한테 추천하고 싶습니다",
                            createdAt =
                                NOW -
                                        45L *
                                        ONE_MINUTE_MILLIS,
                            likeCount =
                                1_000L,
                            replyCount =
                                1L,
                            replies =
                                listOf(
                                    MyReplyUiModel(
                                        nickname =
                                            "a_very_long_nickname_for_test",
                                        profileImageUrl =
                                            PROFILE_IMAGE,
                                        content =
                                            "동의합니다",
                                        createdAt =
                                            NOW -
                                                    ONE_MINUTE_MILLIS,
                                    ),
                                ),
                        ),
                )
            }

            else -> {
                LikedChallengeCommentUiState.Empty
            }
        }

    private const val ONE_MINUTE_MILLIS =
        60_000L

    private const val ONE_HOUR_MILLIS =
        3_600_000L

    private const val ONE_DAY_MILLIS =
        86_400_000L

    private const val REPLY_PROFILE_A =
        "android.resource://com.ssafy.motif/drawable/yeji"
}