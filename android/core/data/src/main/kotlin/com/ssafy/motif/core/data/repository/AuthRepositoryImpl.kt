package com.ssafy.motif.core.data.repository

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.data.auth.SessionAccessTokenProvider
import com.ssafy.motif.core.data.mapper.toModel
import com.ssafy.motif.core.datastore.auth.AuthSessionDataSource
import com.ssafy.motif.core.model.auth.AuthLoginResult
import com.ssafy.motif.core.model.auth.AuthState
import com.ssafy.motif.core.model.onboarding.OnboardingCompletionResult
import com.ssafy.motif.core.network.service.auth.AuthClient
import com.ssafy.motif.core.network.service.onboarding.OnboardingClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import javax.inject.Inject

internal class AuthRepositoryImpl @Inject constructor(
    private val authClient: AuthClient,
    private val onboardingClient: OnboardingClient,
    private val authSessionDataSource: AuthSessionDataSource,
    private val sessionAccessTokenProvider: SessionAccessTokenProvider,
    @param:Dispatcher(MotifDispatcher.IO)
    private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {

    override val authState: Flow<AuthState> =
        authSessionDataSource
            .session
            .onEach { session ->
                sessionAccessTokenProvider
                    .updateAccessToken(
                        session.accessToken,
                    )
            }
            .map { session ->
                when {
                    !session.hasTokens ||
                            session.userId == null -> {
                        AuthState.SignedOut
                    }

                    session.onboardingUserId ==
                            session.userId &&
                            session.onboardingCompleted ==
                            false -> {
                        AuthState.OnboardingRequired
                    }

                    else -> {
                        AuthState.SignedIn
                    }
                }
            }
            .distinctUntilChanged()
            .flowOn(ioDispatcher)

    override fun signInWithGoogle(
        idToken: String,
    ): Flow<AuthLoginResult> =
        flow {
            require(idToken.isNotBlank()) {
                "idToken must not be blank."
            }

            val previousSession =
                authSessionDataSource
                    .session
                    .first()

            val response =
                authClient.signInWithGoogle(
                    idToken = idToken,
                )

            val userId =
                response.user.userSeq

            /*
             * 신규 사용자:
             * 반드시 온보딩 진행.
             *
             * 기존 사용자:
             * 같은 사용자가 이전에 온보딩 중이었다면
             * 미완료 상태를 그대로 유지한다.
             *
             * 그 외 기존 사용자는 완료 상태로 본다.
             */
            val onboardingCompleted =
                when {
                    response.isNewUser -> {
                        false
                    }

                    previousSession.onboardingUserId ==
                            userId &&
                            previousSession.onboardingCompleted ==
                            false -> {
                        false
                    }

                    else -> {
                        true
                    }
                }

            authSessionDataSource.saveSession(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                userId = userId,
                onboardingCompleted =
                    onboardingCompleted,
            )

            /*
             * DataStore Flow 반영을 기다리지 않고
             * 다음 API부터 바로 Authorization 헤더가 붙도록 한다.
             */
            sessionAccessTokenProvider
                .updateAccessToken(
                    response.accessToken,
                )

            emit(
                response.toModel(),
            )
        }.flowOn(ioDispatcher)

    override fun submitOnboarding(
        artistIds: List<Long>,
        nickname: String,
    ): Flow<OnboardingCompletionResult> =
        flow {
            require(artistIds.isNotEmpty()) {
                "At least one artist must be selected."
            }

            require(
                NICKNAME_PATTERN.matches(
                    nickname,
                ),
            ) {
                "Invalid nickname."
            }

            try {
                val response =
                    onboardingClient.completeOnboarding(
                        artistIds = artistIds.distinct(),
                        nickname = nickname,
                    )

                /*
                 * 서버가 온보딩을 정상 완료한 이후에만
                 * 로컬 상태를 완료로 바꾼다.
                 */
                authSessionDataSource.completeOnboarding()

                emit(
                    OnboardingCompletionResult.Completed(
                        subscribedCount =
                            response.subscribedCount,
                        nickname =
                            response.nickname,
                    ),
                )
            } catch (exception: HttpException) {
                when (exception.code()) {
                    HTTP_BAD_REQUEST -> {
                        emit(
                            OnboardingCompletionResult
                                .InvalidParameter,
                        )
                    }

                    HTTP_NOT_FOUND -> {
                        emit(
                            OnboardingCompletionResult
                                .ArtistNotFound,
                        )
                    }

                    HTTP_CONFLICT -> {
                        /*
                         * 서버에서는 이미 온보딩 완료 상태이므로
                         * 로컬 상태도 완료 상태로 맞춘다.
                         */
                        authSessionDataSource.completeOnboarding()

                        emit(
                            OnboardingCompletionResult
                                .AlreadyCompleted,
                        )
                    }

                    else -> {
                        throw exception
                    }
                }
            }
        }.flowOn(ioDispatcher)

    override suspend fun completeOnboarding() {
        withContext(ioDispatcher) {
            authSessionDataSource
                .completeOnboarding()
        }
    }

    private companion object {

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_NOT_FOUND = 404
        const val HTTP_CONFLICT = 409

        val NICKNAME_PATTERN =
            Regex(
                "^[a-zA-Z0-9가-힣._]{2,20}$",
            )
    }
}