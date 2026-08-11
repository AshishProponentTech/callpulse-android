package com.proponenttech.callpulse.di

import com.proponenttech.callpulse.BuildConfig
import com.proponenttech.callpulse.core.network.ApiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * §2 — the base URL exists in exactly one place: the flavor's BuildConfig. This binding is
     * the only route from there into the network layer.
     */
    @Provides
    @Singleton
    fun provideApiConfig(): ApiConfig = object : ApiConfig {
        override val baseUrl: String = BuildConfig.API_BASE_URL
        override val appVersionCode: Int = BuildConfig.VERSION_CODE
        override val appVersion: String = BuildConfig.VERSION_NAME
        override val isDebug: Boolean = BuildConfig.DEBUG
    }
}
