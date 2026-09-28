package com.ssafy.motif.core.notifications.di

import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class NotificationScope

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    @Singleton
    @NotificationScope
    fun provideNotificationScope(
        @Dispatcher(MotifDispatcher.IO)
        dispatcher: CoroutineDispatcher,
    ): CoroutineScope =
        CoroutineScope(
            SupervisorJob() + dispatcher,
        )
}