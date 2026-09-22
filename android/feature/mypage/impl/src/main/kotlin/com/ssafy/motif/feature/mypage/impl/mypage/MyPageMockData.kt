package com.ssafy.motif.feature.mypage.impl.mypage

import com.ssafy.motif.core.model.artist.Artist
import com.ssafy.motif.core.model.artist.ArtistSubscription
import com.ssafy.motif.core.model.comment.Comment
import com.ssafy.motif.core.model.comment.CommentAuthorType
import com.ssafy.motif.core.model.comment.Reply
import com.ssafy.motif.core.model.mypage.LikedChallenge
import com.ssafy.motif.core.model.mypage.LikedChallengeComment
import com.ssafy.motif.core.model.mypage.LikedChallengeCommentState
import com.ssafy.motif.core.model.mypage.MyChallenge
import com.ssafy.motif.core.model.mypage.MyMotif
import com.ssafy.motif.core.model.mypage.UserProfile

internal object MyPageMockData {

    val profile: UserProfile = UserProfile(
        id = 1L,
        nickname = "midzy_01",
        // 미등록 상태 확인용. 이미지가 있는 경우를 보려면 PROFILE_IMAGE로 교체
        profileImageUrl = null,
    )

    const val PROFILE_IMAGE =
        "android.resource://com.ssafy.motif/drawable/profile_square"

    private val CHALLENGE_VIEWS = listOf(
        820_000L, 143_000L, 28_400L, 9_700L, 2_300L, 640L, 88L, 12L,
    )

    private const val CHALLENGE_BASE_TIME = 1_757_900_000_000L
    private const val ONE_DAY_MILLIS = 86_400_000L

    // index가 커질수록 createdAt이 작아지므로 목록 자체가 최신 업로드순이다.
    val challenges: List<MyChallenge> = List(23) { index ->
        MyChallenge(
            id = index + 1L,
            // TODO 마이페이지 전용 썸네일로 교체. 현재는 artistdetail 모듈 리소스를 빌려 씀
            thumbnailUrl = "android.resource://com.ssafy.motif/drawable/ch_${index % 5 + 1}",
            view = CHALLENGE_VIEWS[index % CHALLENGE_VIEWS.size],
            createdAt = CHALLENGE_BASE_TIME - index * ONE_DAY_MILLIS,
        )
    }

    // 패스 보유/미보유, 이미지 유무, 수치 0인 경우를 모두 포함한다.
    val artistSubscriptions: List<ArtistSubscription> = listOf(
        ArtistSubscription(
            Artist(
                id = 1L,
                name = "KARINA",
                groupName = "",
                profileImageUrl = "android.resource://com.ssafy.motif/drawable/yeji"
            ),
            hasMotifPass = true,
            subscribedDays = 421,
            challengeCount = 8L,
            ownedMotifCount = 12L,
            totalMotifCount = 28L,
        ),
        ArtistSubscription(
            Artist(
                id = 2L,
                name = "Ryujin",
                groupName = "RUSH",
                profileImageUrl = PROFILE_IMAGE,
            ),
            hasMotifPass = false,
            subscribedDays = 42,
            challengeCount = 0L,
            ownedMotifCount = 0L,
            totalMotifCount = 15L,
        ),
        ArtistSubscription(
            Artist(
                id = 3L,
                name = "Lia",
                groupName = "",
                profileImageUrl = PROFILE_IMAGE
            ),
            hasMotifPass = true,
            subscribedDays = 128,
            challengeCount = 5L,
            ownedMotifCount = 7L,
            totalMotifCount = 7L,
        ),
        ArtistSubscription(
            Artist(
                id = 4L,
                name = "Chaeryeong",
                groupName = "",
                // 이미지 미등록. 기본 프로필 노출 확인용
                profileImageUrl = "",
            ),
            hasMotifPass = false,
            subscribedDays = 7,
            challengeCount = 2L,
            ownedMotifCount = 1L,
            totalMotifCount = 9L,
        ),
        ArtistSubscription(
            Artist(
                id = 5L,
                name = "Yuna",
                groupName = "",
                profileImageUrl = PROFILE_IMAGE,
            ),
            hasMotifPass = true,
            subscribedDays = 1_024,
            challengeCount = 37L,
            ownedMotifCount = 23L,
            totalMotifCount = 31L,
        ),
    )

    // 대표 모티프. 미설정 상태를 보려면 ViewModel에서 null을 내려주면 된다.
    val representativeMotif: MyMotif = MyMotif(
        id = 1L,
        // TODO 마이페이지 전용 리소스로 교체. 현재는 artistdetail 모듈 것을 빌려 씀
        videoUrl = "android.resource://com.ssafy.motif/raw/test_video",
        thumbnailUrl = "android.resource://com.ssafy.motif/drawable/motif_thumbnail",
    )

    // formatCount의 분기(1000 미만 / k / m)와 경계값을 모두 지나도록 구성했다.
    private val LIKE_COUNTS = listOf(
        1_240_000L, 12_500L, 1_000L, 999L, 128L, 0L, 3_400_000L, 87L, 1_100L,
    )

    private const val LIKED_BASE_TIME = 1_758_100_000_000L
    private const val ONE_HOUR_MILLIS = 3_600_000L

    // formatCreatedAt이 실행 시점 기준으로 계산하므로 고정값이 아닌 현재 시각을 쓴다.
    private val NOW = System.currentTimeMillis()

    private const val ONE_MINUTE_MILLIS = 60_000L
    private const val ONE_DAY = ONE_DAY_MILLIS

    private const val REPLY_PROFILE_A =
        "android.resource://com.ssafy.motif/drawable/yeji"

    private fun myComment(
        id: Long,
        content: String,
        createdAt: Long,
        likeCount: Long,
        replyCount: Long,
    ): Comment = Comment(
        id = id,
        // 내 댓글은 시안에서 프로필과 닉네임을 노출하지 않지만 모델상 필수라 채워둔다.
        profileImageUrl = PROFILE_IMAGE,
        nickname = "kkongdance",
        content = content,
        createdAt = createdAt,
        likeCount = likeCount,
        replyCount = replyCount,
        isLiked = false,
        authorType = CommentAuthorType.CURRENT_USER,
    )

    private fun reply(
        id: Long,
        commentId: Long,
        nickname: String,
        profileImageUrl: String,
        content: String,
        createdAt: Long,
    ): Reply = Reply(
        id = id,
        commentId = commentId,
        profileImageUrl = profileImageUrl,
        nickname = nickname,
        content = content,
        createdAt = createdAt,
        likeCount = 0L,
        isLiked = false,
    )

    // key가 없는 index는 댓글 없음이다. 영상 삭제·댓글 중지·원본 삭제도 모두 이 상태로 표현된다.
    private val MY_COMMENTS: Map<Int, LikedChallengeComment> = mapOf(
        // 시안 기준 화면. 답글이 3개지만 2개까지만 노출되어야 한다.
        0 to LikedChallengeComment(
            comment = myComment(
                id = 101L,
                content = "이 챌린지 예지랑 너무 잘 어울린다🔥",
                createdAt = NOW - 5 * ONE_DAY,
                likeCount = 742L,
                replyCount = 8L,
            ),
            replies = listOf(
                reply(
                    id = 1001L,
                    commentId = 101L,
                    nickname = "dancing_mandu",
                    profileImageUrl = REPLY_PROFILE_A,
                    content = "😂😂😂",
                    createdAt = NOW - 5 * ONE_DAY,
                ),
                reply(
                    id = 1002L,
                    commentId = 101L,
                    nickname = "kkonge",
                    profileImageUrl = PROFILE_IMAGE,
                    content = "이거 보고 바로 챌린지 찍으러 갑니다",
                    createdAt = NOW - 5 * ONE_DAY,
                ),
                // 아래는 화면에 보이면 안 된다.
                reply(
                    id = 1003L,
                    commentId = 101L,
                    nickname = "never_shown",
                    profileImageUrl = PROFILE_IMAGE,
                    content = "세 번째 답글은 노출되지 않아야 한다",
                    createdAt = NOW - 4 * ONE_DAY,
                ),
            ),
        ),
        // 답글 정확히 2개. 시간 표기가 분/시간 단위로 갈리는 경우.
        1 to LikedChallengeComment(
            comment = myComment(
                id = 102L,
                content = "이 동작 진짜 어렵던데 다들 잘하시네요",
                createdAt = NOW - 3 * 60 * ONE_MINUTE_MILLIS,
                likeCount = 12_500L,
                replyCount = 2L,
            ),
            replies = listOf(
                reply(
                    id = 1004L,
                    commentId = 102L,
                    nickname = "motif_lover",
                    profileImageUrl = REPLY_PROFILE_A,
                    content = "저도 삼일 걸렸어요",
                    createdAt = NOW - 42 * ONE_MINUTE_MILLIS,
                ),
                reply(
                    id = 1005L,
                    commentId = 102L,
                    nickname = "sunny",
                    // Reply.profileImageUrl은 널 불가라 빈 문자열로 미등록을 표현한다.
                    profileImageUrl = "",
                    content = "프로필 이미지가 없는 경우",
                    createdAt = NOW - 30_000L,
                ),
            ),
        ),
        // 답글 0개. 댓글만 노출된다.
        2 to LikedChallengeComment(
            comment = myComment(
                id = 103L,
                content = "여기 배경 어디인가요?",
                createdAt = NOW - 20 * ONE_DAY,
                likeCount = 0L,
                replyCount = 0L,
            ),
            replies = emptyList(),
        ),
        // 답글 1개. 본문이 길어 여러 줄로 감기는 경우.
        3 to LikedChallengeComment(
            comment = myComment(
                id = 104L,
                content = "이번 챌린지 음악 선정이 정말 좋았고 안무도 따라하기 쉬워서" +
                        " 처음 도전하는 사람한테 추천하고 싶습니다",
                createdAt = NOW - 45 * ONE_MINUTE_MILLIS,
                likeCount = 1_000L,
                replyCount = 1L,
            ),
            replies = listOf(
                reply(
                    id = 1006L,
                    commentId = 104L,
                    nickname = "a_very_long_nickname_for_test",
                    profileImageUrl = PROFILE_IMAGE,
                    content = "동의합니다",
                    createdAt = NOW - ONE_MINUTE_MILLIS,
                ),
            ),
        ),
    )

    // 댓글 영역이 아예 안 보여야 하는 index. 영상 삭제·댓글 중지·원본 삭제를 대표한다.
    private const val HIDDEN_COMMENT_INDEX = 6

    private fun commentStateOf(index: Int): LikedChallengeCommentState = when {
        index == HIDDEN_COMMENT_INDEX -> LikedChallengeCommentState.Hidden

        else -> MY_COMMENTS[index]
            ?.let(LikedChallengeCommentState::Written)
            ?: LikedChallengeCommentState.Empty
    }

    // index가 커질수록 likedAt이 작아지므로 목록 자체가 최근 좋아요순이다.
    // 17개라 페이지 크기로 나누면 마지막 페이지가 부분적으로 차서 hasNext 확인에 쓸 수 있다.
    val likedChallenges: List<LikedChallenge> = List(17) { index ->
        LikedChallenge(
            id = index + 1L,
            // TODO 마이페이지 전용 리소스로 교체. 현재는 artistdetail 모듈 것을 빌려 씀
            videoUrl = "android.resource://com.ssafy.motif/raw/test_video",
            thumbnailUrl = "android.resource://com.ssafy.motif/drawable/ch_${index % 5 + 1}",
            likeCount = LIKE_COUNTS[index % LIKE_COUNTS.size],
            likedAt = LIKED_BASE_TIME - index * ONE_HOUR_MILLIS,
            commentState = commentStateOf(index),
        )
    }
}
