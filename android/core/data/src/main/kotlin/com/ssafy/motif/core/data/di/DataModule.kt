package com.ssafy.motif.core.data.di

import com.ssafy.motif.core.data.repository.AuthRepository
import com.ssafy.motif.core.data.repository.AuthRepositoryImpl
import com.ssafy.motif.core.data.util.ConnectivityManagerNetworkMonitor
import com.ssafy.motif.core.data.util.NetworkMonitor
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
}