package com.proponenttech.callpulse.core.network

/**
 * §2 — never hardcode the API base URL in more than one place. It lives in the app module's
 * BuildConfig (per flavor) and reaches the network layer only through this binding, so
 * core-network never has to know which flavor it is running in.
 */
interface ApiConfig {
    val baseUrl: String
    val appVersionCode: Int
    val appVersion: String
    val isDebug: Boolean
}

/**
 * Supplies the bearer token to the interceptor. Implemented in core-data, which owns the
 * encrypted storage — declared here so core-network does not depend on core-data.
 */
interface TokenProvider {
    fun currentToken(): String?
}

/**
 * Debug-only base URL override. Retrofit fixes its baseUrl at construction, so an interceptor
 * rewrites the scheme/host/port per request when this returns non-null. Returns null in release
 * builds and whenever no override has been set, in which case the flavor's BuildConfig value
 * stands. Declared here so core-network does not depend on core-data.
 */
interface BaseUrlProvider {
    fun currentBaseUrl(): String?
}
