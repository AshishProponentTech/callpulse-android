package com.proponenttech.callpulse.core.data.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §12 — the permission set, and only that set.
 *
 * Deliberately absent and never to be added: READ_SMS, RECORD_AUDIO, anything touching message
 * content or the address book (CLAUDE.md rule 4).
 */
@Singleton
class PermissionState @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Core. Without it the app is useless and must say so rather than fail silently (§12). */
    val callLog: Boolean get() = granted(Manifest.permission.READ_CALL_LOG)

    val phoneState: Boolean get() = granted(Manifest.permission.READ_PHONE_STATE)

    /** Optional — the app must work fully without it; it only turns a number into a name. */
    val contacts: Boolean get() = granted(Manifest.permission.READ_CONTACTS)

    val notifications: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            true // Not a runtime permission before API 33.
        }

    val batteryOptimizationIgnored: Boolean
        get() = context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) ?: false

    /** Capture cannot function without this one. */
    val canCapture: Boolean get() = callLog

    /** §12.1 — drives the persistent in-app banner. */
    val hasAllRequired: Boolean get() = callLog && phoneState && notifications
}
