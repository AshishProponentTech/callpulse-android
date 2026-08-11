package com.proponenttech.callpulse

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class CallPulseApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        /*
         * §11.3 — nothing sensitive in logs. The debug tree is planted only in debug builds;
         * a release build plants nothing, so no phone number or contact name can ever reach
         * logcat on an employee's device.
         */
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
