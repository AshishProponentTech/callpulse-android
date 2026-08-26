package com.proponenttech.callpulse.core.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** §15 — when sync runs. */
@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val workManager get() = WorkManager.getInstance(context)

    private val networkRequired = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Triggered on draft created, on review saved, and on app foreground. */
    fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkRequired)
            // §15 — exponential backoff from 30s.
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniqueWork(SyncWorker.NAME, ExistingWorkPolicy.REPLACE, request)
    }

    /** §15 — every 15 minutes, WorkManager's floor for periodic work. */
    fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkRequired)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.NAME + "_periodic",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** §12.1 — every 6h. */
    fun ensurePeriodicHeartbeat() {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(6, TimeUnit.HOURS)
            .setConstraints(networkRequired)
            .build()

        workManager.enqueueUniquePeriodicWork(
            HeartbeatWorker.NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun heartbeatNow() {
        val request = OneTimeWorkRequestBuilder<HeartbeatWorker>()
            .setConstraints(networkRequired)
            .build()

        workManager.enqueueUniqueWork(
            HeartbeatWorker.NAME + "_now",
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
