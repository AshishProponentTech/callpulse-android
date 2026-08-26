package com.proponenttech.callpulse.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.proponenttech.callpulse.core.data.device.DeviceRepository
import com.proponenttech.callpulse.core.data.session.SecureStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * §12.1 — every 6h and on app open. Reports permission health so a manager can see that an
 * employee turned something off, without the app having to nag them in person.
 */
@HiltWorker
class HeartbeatWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val deviceRepository: DeviceRepository,
    private val secureStore: SecureStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (secureStore.token == null) return Result.success()

        return try {
            deviceRepository.sendHeartbeat()
            Result.success()
        } catch (e: Exception) {
            Timber.w(e, "Heartbeat failed")
            Result.success() // Never escalate; the next run will try again.
        }
    }

    companion object {
        const val NAME = "callpulse_heartbeat"
    }
}
