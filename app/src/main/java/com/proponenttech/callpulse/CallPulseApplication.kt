package com.proponenttech.callpulse

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.proponenttech.callpulse.core.common.PhoneNumbers
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CallPulseApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * Workers are constructed by WorkManager, so they need Hilt's factory to receive their
     * dependencies. Without this every @HiltWorker fails to instantiate at runtime.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        /*
         * §11.3 — nothing sensitive in logs. The debug tree is planted only in debug builds, so
         * no phone number or contact name can reach logcat on an employee's device.
         */
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // libphonenumber-android loads its metadata from assets and needs a Context.
        PhoneNumbers.init(this)
    }
}
