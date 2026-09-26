package com.proponenttech.callpulse.feature.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.proponenttech.callpulse.core.data.sync.SyncScheduler
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * §13.1 mechanism 4. Nothing in the original spec restarted capture after a reboot, so calls
 * made between boot and the employee next opening the app were lost.
 *
 * Re-arms the periodic worker (WorkManager restores its own queue, but this is cheap insurance)
 * and runs one immediate scan to pick up anything that happened while the device was off.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: CaptureScheduler

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Timber.i("Boot completed - re-arming capture and upload")
        scheduler.ensurePeriodicReconcile()
        scheduler.scanNow()

        // Capture without upload just fills the local queue. Re-arm both, and flush anything
        // that accumulated while the device was off.
        syncScheduler.ensurePeriodicSync()
        syncScheduler.ensurePeriodicHeartbeat()
        syncScheduler.syncNow()
    }
}
