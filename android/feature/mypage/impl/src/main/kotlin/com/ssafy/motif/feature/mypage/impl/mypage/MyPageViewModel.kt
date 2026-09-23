package com.ssafy.motif.feature.mypage.impl.mypage

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssafy.motif.core.common.result.Result
import com.ssafy.motif.core.common.result.asResult
import com.ssafy.motif.core.data.repository.UserRepository
import com.ssafy.motif.core.model.user.UserActivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
internal class MyPageViewModel @Inject constructor(
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            MyPageUiState(),
        )

    val uiState: StateFlow<MyPageUiState> =
        _uiState.asStateFlow()

    private var overviewLoadJob: Job? = null
    private var videoLoadJob: Job? = null
    private var subscriptionLoadJob: Job? = null
    private var activityLoadJob: Job? = null
    private var activityAppendJob: Job? = null
    private var nicknameUpdateJob: Job? = null
    private var profileImageUpdateJob: Job? = null

    init {
        loadOverview()
    }

    fun selectTab(
        tab: MyPageTab,
    ) {
        _uiState.update { state ->
            state.copy(
                selectedTab = tab,
            )
        }

        when (tab) {
            MyPageTab.Challenge -> Unit

            MyPageTab.Artist -> {
                if (
                    _uiState.value.artistState
                            is MyArtistUiState.Idle
                ) {
                    loadSubscriptions()
                }
            }

            MyPageTab.Liked -> {
                if (
                    _uiState.value.likedState
                            is MyLikedUiState.Idle
                ) {
                    loadActivities()
                }
            }
        }
    }

    fun retryOverview() {
        loadOverview()
    }

    fun retryVideos() {
        loadVideos()
    }

    fun retrySubscriptions() {
        loadSubscriptions()
    }

    fun retryLiked() {
        loadActivities()
    }

    fun onArtistCardClick(
        artistId: Long,
    ) {
        _uiState.update { state ->
            state.copy(
                flippedArtistId =
                    if (
                        state.flippedArtistId ==
                        artistId
                    ) {
                        null
                    } else {
                        artistId
                    },
            )
        }
    }

    fun onLikedFocusChange(
        index: Int,
    ) {
        _uiState.update { state ->
            state.copy(
                likedFocusedIndex = index,
            )
        }
    }

    fun onNicknameEditStart() {
        val profile =
            (
                    _uiState.value.profileState
                            as? ProfileUiState.Success
                    )
                ?.profile
                ?: return

        _uiState.update { state ->
            state.copy(
                nicknameEdit =
                    NicknameEditUiState(
                        value = profile.nickname,
                    ),
            )
        }
    }

    fun onNicknameChange(
        value: String,
    ) {
        if (
            value.length >
            MAX_NICKNAME_LENGTH
        ) {
            return
        }

        _uiState.update { state ->
            val edit =
                state.nicknameEdit
                    ?: return@update state

            state.copy(
                nicknameEdit =
                    edit.copy(
                        value = value,
                        errorMessage = null,
                    ),
            )
        }
    }

    fun onNicknameEditCancel() {
        nicknameUpdateJob?.cancel()

        _uiState.update { state ->
            state.copy(
                nicknameEdit = null,
            )
        }
    }

    fun onNicknameEditDone() {
        val edit =
            _uiState.value.nicknameEdit
                ?: return

        if (edit.isSaving) {
            return
        }

        val nickname =
            edit.value

        if (
            !NICKNAME_REGEX.matches(
                nickname,
            )
        ) {
            _uiState.update { state ->
                state.copy(
                    nicknameEdit =
                        edit.copy(
                            errorMessage =
                                "2~20자의 영문·한글·숫자·마침표·언더바만 허용합니다.",
                        ),
                )
            }

            return
        }

        nicknameUpdateJob?.cancel()

        nicknameUpdateJob =
            userRepository
                .updateNickname(
                    nickname = nickname,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                val currentEdit =
                                    state.nicknameEdit
                                        ?: return@update state

                                state.copy(
                                    nicknameEdit =
                                        currentEdit.copy(
                                            isSaving = true,
                                            errorMessage = null,
                                        ),
                                )
                            }
                        }

                        is Result.Success -> {
                            _uiState.update { state ->
                                val profileState =
                                    state.profileState

                                val updatedProfileState =
                                    if (
                                        profileState
                                                is ProfileUiState.Success
                                    ) {
                                        ProfileUiState.Success(
                                            profile =
                                                profileState
                                                    .profile
                                                    .copy(
                                                        nickname =
                                                            result.data,
                                                    ),
                                        )
                                    } else {
                                        profileState
                                    }

                                state.copy(
                                    profileState =
                                        updatedProfileState,
                                    nicknameEdit = null,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                val currentEdit =
                                    state.nicknameEdit
                                        ?: return@update state

                                state.copy(
                                    nicknameEdit =
                                        currentEdit.copy(
                                            isSaving = false,
                                            errorMessage =
                                                result.exception.message
                                                    ?: "닉네임 변경에 실패했습니다.",
                                        ),
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun updateProfileImage(
        contentType: String,
        imageBytes: ByteArray,
    ) {
        if (
            _uiState.value
                .isProfileImageUpdating
        ) {
            return
        }

        profileImageUpdateJob?.cancel()

        profileImageUpdateJob =
            userRepository
                .updateProfileImage(
                    contentType = contentType,
                    imageBytes = imageBytes,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                state.copy(
                                    isProfileImageUpdating =
                                        true,
                                    profileImageErrorMessage =
                                        null,
                                )
                            }
                        }

                        is Result.Success -> {
                            _uiState.update { state ->
                                val profileState =
                                    state.profileState

                                val updatedProfileState =
                                    if (
                                        profileState
                                                is ProfileUiState.Success
                                    ) {
                                        ProfileUiState.Success(
                                            profile =
                                                profileState
                                                    .profile
                                                    .copy(
                                                        profileImageUrl =
                                                            result.data,
                                                    ),
                                        )
                                    } else {
                                        profileState
                                    }

                                state.copy(
                                    profileState =
                                        updatedProfileState,
                                    isProfileImageUpdating =
                                        false,
                                    profileImageErrorMessage =
                                        null,
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    isProfileImageUpdating =
                                        false,
                                    profileImageErrorMessage =
                                        result.exception.message
                                            ?: "프로필 이미지 변경에 실패했습니다.",
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    fun onProfileImageSelectionError(
        throwable: Throwable,
    ) {
        _uiState.update { state ->
            state.copy(
                profileImageErrorMessage =
                    throwable.message
                        ?: "프로필 이미지를 불러오지 못했습니다.",
            )
        }
    }

    fun onLoadMoreLiked() {
        val state =
            _uiState.value.likedState
                    as? MyLikedUiState.Success
                ?: return

        if (
            !state.hasNext ||
            state.appendState
                    !is MyLikedAppendUiState.Idle
        ) {
            return
        }

        val nextCursor =
            state.nextCursor
                ?: return

        loadNextActivities(
            cursor = nextCursor,
        )
    }

    fun retryLoadMoreLiked() {
        val state =
            _uiState.value.likedState
                    as? MyLikedUiState.Success
                ?: return

        if (
            !state.hasNext ||
            state.appendState
                    !is MyLikedAppendUiState.Error
        ) {
            return
        }

        val nextCursor =
            state.nextCursor
                ?: return

        loadNextActivities(
            cursor = nextCursor,
        )
    }

    private fun loadOverview() {
        overviewLoadJob?.cancel()

        overviewLoadJob =
            userRepository
                .getUserOverview()
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            _uiState.update { state ->
                                state.copy(
                                    profileState =
                                        ProfileUiState.Loading,
                                    challengeState =
                                        MyChallengeUiState.Loading,
                                )
                            }
                        }

                        is Result.Success -> {
                            val overview =
                                result.data

                            _uiState.update { state ->
                                state.copy(
                                    profileState =
                                        ProfileUiState.Success(
                                            profile =
                                                overview.profile,
                                        ),
                                    representativeMotif =
                                        overview.representativeMotif,
                                    challengeState =
                                        MyChallengeUiState.Success(
                                            challenges =
                                                overview.videos,
                                        ),
                                )
                            }

                            loadVideos()
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                state.copy(
                                    profileState =
                                        ProfileUiState.Error(
                                            exception =
                                                result.exception,
                                        ),
                                    challengeState =
                                        MyChallengeUiState.Error(
                                            exception =
                                                result.exception,
                                        ),
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadVideos() {
        videoLoadJob?.cancel()

        videoLoadJob =
            userRepository
                .getVideos()
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> {
                            /*
                             * GET /users/me 에서 최신 9개 프리뷰를
                             * 이미 받은 상태일 수 있으므로,
                             * 전체 목록 조회 중에는 기존 화면을 유지한다.
                             */
                            if (
                                _uiState.value.challengeState
                                        !is MyChallengeUiState.Success
                            ) {
                                _uiState.update { state ->
                                    state.copy(
                                        challengeState =
                                            MyChallengeUiState.Loading,
                                    )
                                }
                            }
                        }

                        is Result.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    challengeState =
                                        MyChallengeUiState.Success(
                                            challenges =
                                                result.data,
                                        ),
                                )
                            }
                        }

                        is Result.Error -> {
                            /*
                             * /users/me 에서 받은 최신 9개가 있으면
                             * 전체 목록 호출 실패 시에도 그대로 유지한다.
                             */
                            if (
                                _uiState.value.challengeState
                                        !is MyChallengeUiState.Success
                            ) {
                                _uiState.update { state ->
                                    state.copy(
                                        challengeState =
                                            MyChallengeUiState.Error(
                                                exception =
                                                    result.exception,
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadSubscriptions() {
        subscriptionLoadJob?.cancel()

        subscriptionLoadJob =
            userRepository
                .getSubscriptions()
                .asResult()
                .onEach { result ->
                    _uiState.update { state ->
                        when (result) {
                            Result.Loading -> {
                                state.copy(
                                    artistState =
                                        MyArtistUiState.Loading,
                                )
                            }

                            is Result.Success -> {
                                state.copy(
                                    artistState =
                                        MyArtistUiState.Success(
                                            artists =
                                                result.data,
                                        ),
                                )
                            }

                            is Result.Error -> {
                                state.copy(
                                    artistState =
                                        MyArtistUiState.Error(
                                            exception =
                                                result.exception,
                                        ),
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadActivities() {
        activityLoadJob?.cancel()
        activityAppendJob?.cancel()

        activityLoadJob =
            userRepository
                .getActivities(
                    cursor = null,
                )
                .asResult()
                .onEach { result ->
                    _uiState.update { state ->
                        when (result) {
                            Result.Loading -> {
                                state.copy(
                                    likedState =
                                        MyLikedUiState.Loading,
                                )
                            }

                            is Result.Success -> {
                                state.copy(
                                    likedState =
                                        MyLikedUiState.Success(
                                            challenges =
                                                result
                                                    .data
                                                    .items
                                                    .map(
                                                        UserActivity::toLikedChallengeUiModel,
                                                    ),
                                            nextCursor =
                                                result
                                                    .data
                                                    .nextCursor,
                                            hasNext =
                                                result
                                                    .data
                                                    .hasNext,
                                        ),
                                    likedFocusedIndex =
                                        0,
                                )
                            }

                            is Result.Error -> {
                                state.copy(
                                    likedState =
                                        MyLikedUiState.Error(
                                            exception =
                                                result.exception,
                                        ),
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private fun loadNextActivities(
        cursor: String,
    ) {
        _uiState.update { state ->
            val likedState =
                state.likedState
                        as? MyLikedUiState.Success
                    ?: return@update state

            state.copy(
                likedState =
                    likedState.copy(
                        appendState =
                            MyLikedAppendUiState.Loading,
                    ),
            )
        }

        activityAppendJob?.cancel()

        activityAppendJob =
            userRepository
                .getActivities(
                    cursor = cursor,
                )
                .asResult()
                .onEach { result ->
                    when (result) {
                        Result.Loading -> Unit

                        is Result.Success -> {
                            _uiState.update { state ->
                                val likedState =
                                    state.likedState
                                            as? MyLikedUiState.Success
                                        ?: return@update state

                                val newChallenges =
                                    result
                                        .data
                                        .items
                                        .map(
                                            UserActivity::toLikedChallengeUiModel,
                                        )

                                state.copy(
                                    likedState =
                                        likedState.copy(
                                            challenges =
                                                (
                                                        likedState.challenges +
                                                                newChallenges
                                                        )
                                                    .distinctBy {
                                                        it.id
                                                    },
                                            nextCursor =
                                                result
                                                    .data
                                                    .nextCursor,
                                            hasNext =
                                                result
                                                    .data
                                                    .hasNext,
                                            appendState =
                                                MyLikedAppendUiState.Idle,
                                        ),
                                )
                            }
                        }

                        is Result.Error -> {
                            _uiState.update { state ->
                                val likedState =
                                    state.likedState
                                            as? MyLikedUiState.Success
                                        ?: return@update state

                                state.copy(
                                    likedState =
                                        likedState.copy(
                                            appendState =
                                                MyLikedAppendUiState.Error(
                                                    exception =
                                                        result.exception,
                                                ),
                                        ),
                                )
                            }
                        }
                    }
                }
                .launchIn(
                    viewModelScope,
                )
    }

    private companion object {

        const val MAX_NICKNAME_LENGTH =
            20

        val NICKNAME_REGEX =
            Regex(
                pattern =
                    "^[A-Za-z0-9가-힣._]{2,20}$",
            )
    }
}

private fun UserActivity.toLikedChallengeUiModel():
        LikedChallengeUiModel =
    LikedChallengeUiModel(
        id =
            video.id,
        thumbnailUrl =
            video.thumbnailUrl,
        likeCount =
            video.likeCount,
        commentState =
            myComment
                ?.let { comment ->
                    LikedChallengeCommentUiState.Written(
                        myComment =
                            MyCommentUiModel(
                                content =
                                    comment.content,
                                createdAt =
                                    comment.createdAt,
                                likeCount =
                                    comment.likeCount,
                                replyCount =
                                    comment.replyCount,
                                replies =
                                    replies.map { reply ->
                                        MyReplyUiModel(
                                            nickname =
                                                reply.author.nickname,
                                            profileImageUrl =
                                                reply.author.profileImageUrl,
                                            content =
                                                reply.content,
                                            createdAt =
                                                reply.createdAt,
                                        )
                                    },
                            ),
                    )
                }
                ?: LikedChallengeCommentUiState.Empty,
    )