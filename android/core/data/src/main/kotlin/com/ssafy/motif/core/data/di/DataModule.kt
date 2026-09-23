package com.ssafy.motif.core.data.di

import com.ssafy.motif.core.data.auth.SessionAccessTokenProvider
import com.ssafy.motif.core.data.repository.ArtistRepository
import com.ssafy.motif.core.data.repository.ArtistRepositoryImpl
import com.ssafy.motif.core.data.repository.AuthRepository
import com.ssafy.motif.core.data.repository.AuthRepositoryImpl
import com.ssafy.motif.core.data.repository.ChallengeRepository
import com.ssafy.motif.core.data.repository.ChallengeRepositoryImpl
import com.ssafy.motif.core.data.repository.CommentRepository
import com.ssafy.motif.core.data.repository.CommentRepositoryImpl
import com.ssafy.motif.core.data.repository.NoticeRepository
import com.ssafy.motif.core.data.repository.NoticeRepositoryImpl
import com.ssafy.motif.core.data.repository.UserRepository
import com.ssafy.motif.core.data.repository.UserRepositoryImpl
import com.ssafy.motif.core.data.util.ConnectivityManagerNetworkMonitor
import com.ssafy.motif.core.data.util.NetworkMonitor
import com.ssafy.motif.core.network.auth.AccessTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    internal abstract fun bindsNetworkMonitor(
        networkMonitor: ConnectivityManagerNetworkMonitor,
    ): NetworkMonitor

    @Binds
    internal abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl,
    ): AuthRepository

    @Binds
    internal abstract fun bindChallengeRepository(
        challengeRepositoryImpl: ChallengeRepositoryImpl,
    ): ChallengeRepository

    @Binds
    internal abstract fun bindArtistRepository(
        artistRepositoryImpl: ArtistRepositoryImpl,
    ): ArtistRepository

    @Binds
    internal abstract fun bindsCommentRepository(
        commentRepositoryImpl: CommentRepositoryImpl,
    ): CommentRepository

    @Binds
    internal abstract fun bindsNoticeRepository(
        noticeRepositoryImpl: NoticeRepositoryImpl,
    ): NoticeRepository

    @Binds
    internal abstract fun bindUserRepository(
        userRepositoryImpl: UserRepositoryImpl,
    ): UserRepository

    @Binds
    internal abstract fun bindAccessTokenProvider(
        provider: SessionAccessTokenProvider,
    ): AccessTokenProvider
}