package com.ssafy.motif.core.network.di

import com.ssafy.motif.core.network.BuildConfig
import com.ssafy.motif.core.network.NetworkConfig
import com.ssafy.motif.core.network.auth.AccessTokenInterceptor
import com.ssafy.motif.core.network.service.artist.ArtistService
import com.ssafy.motif.core.network.service.challenge.ChallengeService
import com.ssafy.motif.core.network.service.comment.CommentService
import com.ssafy.motif.core.network.service.composite.CompositeJobService
import com.ssafy.motif.core.network.service.notice.NoticeService
import com.ssafy.motif.core.network.service.user.UserService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        accessTokenInterceptor: AccessTokenInterceptor,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor(
                accessTokenInterceptor,
            )
            .connectTimeout(
                NETWORK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            .readTimeout(
                NETWORK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            .writeTimeout(
                NETWORK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            .apply {
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(
                        HttpLoggingInterceptor()
                            .apply {
                                level =
                                    HttpLoggingInterceptor
                                        .Level
                                        .BODY

                                redactHeader(
                                    AUTHORIZATION_HEADER,
                                )
                            },
                    )
                }
            }
            .build()

    @Provides
    @Singleton
    @UploadHttpClient
    fun provideUploadHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(
                NETWORK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS,
            )
            .readTimeout(
                UPLOAD_TIMEOUT_MINUTES,
                TimeUnit.MINUTES,
            )
            .writeTimeout(
                UPLOAD_TIMEOUT_MINUTES,
                TimeUnit.MINUTES,
            )
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        json: Json,
        okHttpClient: OkHttpClient,
    ): Retrofit =
        Retrofit
            .Builder()
            .baseUrl(
                NetworkConfig.BASE_URL,
            )
            .client(
                okHttpClient,
            )
            .addConverterFactory(
                json.asConverterFactory(
                    CONTENT_TYPE_JSON
                        .toMediaType(),
                ),
            )
            .build()

    @Provides
    @Singleton
    fun provideNoticeService(
        retrofit: Retrofit,
    ): NoticeService =
        retrofit.create(
            NoticeService::class.java,
        )

    @Provides
    @Singleton
    fun provideChallengeService(
        retrofit: Retrofit,
    ): ChallengeService =
        retrofit.create(
            ChallengeService::class.java,
        )

    @Provides
    @Singleton
    fun provideArtistService(
        retrofit: Retrofit,
    ): ArtistService =
        retrofit.create(
            ArtistService::class.java,
        )

    @Provides
    @Singleton
    fun provideCommentService(
        retrofit: Retrofit,
    ): CommentService =
        retrofit.create(
            CommentService::class.java,
        )

    @Provides
    @Singleton
    fun provideCompositeJobService(
        retrofit: Retrofit,
    ): CompositeJobService =
        retrofit.create(
            CompositeJobService::class.java,
        )

    @Provides
    @Singleton
    fun provideUserService(
        retrofit: Retrofit,
    ): UserService =
        retrofit.create(
            UserService::class.java,
        )

    private const val NETWORK_TIMEOUT_SECONDS = 30L
    private const val UPLOAD_TIMEOUT_MINUTES = 5L

    private const val CONTENT_TYPE_JSON =
        "application/json"

    private const val AUTHORIZATION_HEADER =
        "Authorization"
}