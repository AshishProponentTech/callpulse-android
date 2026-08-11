package com.proponenttech.callpulse.core.data.di

import com.proponenttech.callpulse.core.data.session.SecureStore
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
}
