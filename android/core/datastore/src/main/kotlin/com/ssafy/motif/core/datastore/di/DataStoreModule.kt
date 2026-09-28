package com.ssafy.motif.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.ssafy.motif.core.common.network.Dispatcher
import com.ssafy.motif.core.common.network.MotifDispatcher
import com.ssafy.motif.core.common.network.di.ApplicationScope
import com.ssafy.motif.core.datastore.AuthSessionPreferences
import com.ssafy.motif.core.datastore.auth.AuthSessionSerializer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    internal fun providesAuthSessionDataStore(
        @ApplicationContext context: Context,
        @Dispatcher(MotifDispatcher.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
        authSessionSerializer: AuthSessionSerializer,
    ): DataStore<AuthSessionPreferences> =
        DataStoreFactory.create(
            serializer = authSessionSerializer,
            scope = CoroutineScope(scope.coroutineContext + ioDispatcher),
        ) {
            context.dataStoreFile("auth_session.pb")
        }
}