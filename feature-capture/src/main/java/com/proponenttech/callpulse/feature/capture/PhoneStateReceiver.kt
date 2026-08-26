package com.proponenttech.callpulse.feature.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

/**
 * §13.1 mechanism 1.
 *
 * OFFHOOK means a call is in progress; IDLE means it ended, which is when the call log row is
 * about to appear. This is an OPTIMISATION for promptness, not the reliability mechanism —
 * OEM battery managers kill manifest receivers routinely, which is exactly why
 * PeriodicReconcileWorker exists and why nothing here is allowed to be the only path.
 */
@AndroidEntryPoint
class PhoneStateReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: CaptureScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                Timber.d("Call in progress")
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                Timber.d("Call ended - scheduling a scan")
                scheduler.scanAfterCall()
            }
        }
    }
}
