package com.proponenttech.callpulse.core.data.di

import com.proponenttech.callpulse.core.data.session.DevSettings
import com.proponenttech.callpulse.core.data.session.SecureStore
import com.proponenttech.callpulse.core.network.ApiConfig
import com.proponenttech.callpulse.core.network.BaseUrlProvider
import com.proponenttech.callpulse.core.network.TokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * Bridges core-network's TokenProvider to the encrypted store, so the OkHttp interceptor can
     * read the token without core-network depending on core-data.
     */
    @Provides
    @Singleton
    fun provideTokenProvider(secureStore: SecureStore): TokenProvider = object : TokenProvider {
        override fun currentToken(): String? = secureStore.token
    }

    /**
     * Release builds always return null, so the flavor's BuildConfig URL is the only address a
     * production APK can ever talk to — the override cannot be turned on in the field.
     */
    @Provides
    @Singleton
    fun provideBaseUrlProvider(
        devSettings: DevSettings,
        config: ApiConfig,
    ): BaseUrlProvider = object : BaseUrlProvider {
        override fun currentBaseUrl(): String? =
            if (config.isDebug) devSettings.baseUrlOverride else null
    }
}
