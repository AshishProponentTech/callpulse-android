package com.proponenttech.callpulse.core.data.session

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Debug-only overrides.
 *
 * The staging server lives on a development machine whose LAN address is handed out by DHCP and
 * has already moved twice mid-project. Pinning it at build time meant every reassignment cost a
 * rebuild, a reinstall, and a confusing "nothing happens" on the phone. This lets the address be
 * corrected on the device instead.
 *
 * Plain SharedPreferences rather than DataStore because the OkHttp interceptor reads it on the
 * network thread and needs a synchronous answer. Nothing secret is stored here.
 */
@Singleton
class DevSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("callpulse_dev", Context.MODE_PRIVATE)

    /** Null means "use the flavor's BuildConfig.API_BASE_URL". */
    var baseUrlOverride: String?
        get() = prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                val cleaned = value?.trim()?.takeIf { it.isNotBlank() }
                if (cleaned == null) remove(KEY_BASE_URL) else putString(KEY_BASE_URL, normalise(cleaned))
            }.apply()
        }

    /**
     * Accepts what someone actually types — "192.168.1.45", "192.168.1.45:8123",
     * "http://192.168.1.45:8123", or a full ".../api/v1/" — and produces a URL Retrofit
     * accepts. Retrofit requires the base URL to end in "/".
     */
    private fun normalise(input: String): String {
        var url = input.trim()

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url
        }
        url = url.trimEnd('/')

        val authority = url.removePrefix("http://").removePrefix("https://").substringBefore('/')
        if (!authority.contains(':')) {
            url = url.replaceFirst(authority, authority + ":8123")
        }

        if (!url.contains("/api/")) {
            url = "$url/api/v1"
        }

        return "$url/"
    }

    private companion object {
        const val KEY_BASE_URL = "base_url_override"
    }
}
