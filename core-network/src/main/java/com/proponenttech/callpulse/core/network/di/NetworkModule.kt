package com.proponenttech.callpulse.core.network.di

import com.proponenttech.callpulse.core.network.ApiCall
import com.proponenttech.callpulse.core.network.ApiConfig
import com.proponenttech.callpulse.core.network.CallPulseApi
import com.proponenttech.callpulse.core.network.TokenProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true // The server may add fields; an older app must not break.
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttp(
        config: ApiConfig,
        tokenProvider: TokenProvider,
    ): OkHttpClient {
        val headers = Interceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept", "application/json")
                // §21 — sent on every request so the server can enforce min_supported_version_code.
                .header("X-App-Version-Code", config.appVersionCode.toString())

            tokenProvider.currentToken()?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }

            chain.proceed(builder.build())
        }

        return OkHttpClient.Builder()
            .addInterceptor(headers)
            .apply {
                if (config.isDebug) {
                    /*
                     * §11.3 — nothing sensitive in logs. BODY logging would print phone numbers
                     * and contact names, so even in debug this stays at BASIC (method, URL,
                     * status, timing).
                     */
                    addInterceptor(
                        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
                    )
                }
            }
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, config: ApiConfig): Retrofit =
        Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): CallPulseApi = retrofit.create(CallPulseApi::class.java)

    @Provides
    @Singleton
    fun provideApiCall(json: Json): ApiCall = ApiCall(json)
}
