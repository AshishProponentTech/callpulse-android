package com.proponenttech.callpulse.feature.capture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §13.5 — the post-call review prompt.
 *
 * Dismissing it leaves the draft unreviewed; the call still counts toward busy time. The prompt
 * is a convenience, never the thing that makes a call real.
 */
@Singleton
class ReviewNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Captured calls",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Confirms calls captured for YogatribexCRM."
            setShowBadge(true)
        }

        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * §13.5 — coalesced. Three calls in five minutes produce one "3 calls need review", not
     * three separate notifications competing for attention.
     */
    fun notifyCapture(newCount: Int, totalUnreviewed: Int, latestLabel: String?) {
        if (newCount <= 0 || !canPost()) return

        ensureChannel()

        val title = "New call captured"
        val text = "New call captured — uploads to YogatribexCRM automatically"

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pending = PendingIntent.getActivity(
            context,
            0,
            launch ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }

    companion object {
        /**
         * Internal, not private: CallLogScanWorker posts its expedited-work foreground
         * notification on this same channel, so the employee sees one "CallPulse" channel in
         * system settings rather than two they might silence independently.
         */
        internal const val CHANNEL_ID = "call_review"

        private const val NOTIFICATION_ID = 1001
    }
}
