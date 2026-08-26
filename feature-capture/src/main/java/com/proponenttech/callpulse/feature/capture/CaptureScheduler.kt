package com.proponenttech.callpulse.feature.capture

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One place that decides when capture runs (§13.1). */
@Singleton
class CaptureScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val workManager get() = WorkManager.getInstance(context)

    /**
     * Called when a call has just ended. Expedited so it runs promptly, with backoff because the
     * call log row lands slightly after IDLE (§13.1).
     */
    fun scanAfterCall() {
        val request = OneTimeWorkRequestBuilder<CallLogScanWorker>()
            .setInputData(Data.Builder().putBoolean(CallLogScanWorker.KEY_EXPECT_NEW_CALL, true).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 5, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        // REPLACE, not APPEND: two calls in quick succession need one scan, not two.
        workManager.enqueueUniqueWork(
            CallLogScanWorker.NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Timber.d("Post-call scan enqueued")
    }

    /** An immediate one-off scan — app foreground, boot, permission just granted. */
    fun scanNow() {
        val request = OneTimeWorkRequestBuilder<CallLogScanWorker>().build()
        workManager.enqueueUniqueWork(
            CallLogScanWorker.NAME + "_now",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * §13.1 — every 15 minutes, which is WorkManager's floor for periodic work. KEEP so that
     * re-registering on every app start does not reset the interval.
     */
    fun ensurePeriodicReconcile() {
        val request = PeriodicWorkRequestBuilder<PeriodicReconcileWorker>(15, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PeriodicReconcileWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PeriodicReconcileWorker.NAME)
        workManager.cancelUniqueWork(CallLogScanWorker.NAME)
    }
}
