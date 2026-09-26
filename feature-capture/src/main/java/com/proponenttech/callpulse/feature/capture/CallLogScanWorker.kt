package com.proponenttech.callpulse.feature.capture

import android.content.Context
import androidx.hilt.work.HiltWorker
import android.app.Notification
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.data.sync.SyncScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * §13.1 mechanism 2 — the scan. Enqueued expedited by the PhoneState receiver, and periodically
 * by the reconcile worker.
 *
 * The call log row is written slightly AFTER the call reaches IDLE, so an immediate scan can
 * find nothing. Rather than sleeping blindly, the worker retries: WorkManager re-runs it with
 * backoff when it returns retry().
 */
@HiltWorker
class CallLogScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val captureRepository: CaptureRepository,
    private val dao: CallSessionDao,
    private val notifier: ReviewNotifier,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(appContext, params) {

    /**
     * Required because scanAfterCall() marks this work expedited.
     *
     * On API < 31 WorkManager runs expedited work as a foreground service and calls this;
     * CoroutineWorker's default implementation throws IllegalStateException, so the post-call
     * scan died immediately on every Android 8-11 handset. minSdk is 26, so that is a real
     * slice of the fleet, and the failure was silent — the periodic reconcile picked the call
     * up later, which is exactly the kind of bug that hides behind a working fallback.
     */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val notification: Notification = NotificationCompat.Builder(applicationContext, ReviewNotifier.CHANNEL_ID)
            .setContentTitle("Updating call log")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(SCAN_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(SCAN_NOTIFICATION_ID, notification)
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val result = captureRepository.scan()

            if (result.created > 0) {
                /*
                 * Capture is hands-off: the call reaches the server without the employee opening
                 * the app or reviewing anything. §13.5 is explicit that an unreviewed call still
                 * counts, so waiting for a review before syncing would lose data from anyone who
                 * simply never taps the notification.
                 */
                syncScheduler.syncNow()

                val unreviewed = dao.observeUnreviewedCount().first()
                notifier.notifyCapture(
                    newCount = result.created,
                    totalUnreviewed = unreviewed,
                    latestLabel = null,
                )
                return Result.success()
            }

            /*
             * Nothing new. If this run was triggered by a call ending, the row may not have been
             * written yet — retry with backoff rather than losing the call. Bounded so a quiet
             * device does not retry forever.
             */
            if (inputData.getBoolean(KEY_EXPECT_NEW_CALL, false) && runAttemptCount < MAX_ATTEMPTS) {
                Timber.d("No new rows yet (attempt %d) - retrying", runAttemptCount + 1)
                return Result.retry()
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Call log scan failed")
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val SCAN_NOTIFICATION_ID = 1002

        const val NAME = "callpulse_call_log_scan"
        const val KEY_EXPECT_NEW_CALL = "expect_new_call"
        private const val MAX_ATTEMPTS = 3
    }
}
