package com.proponenttech.callpulse.feature.capture

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.data.sync.SyncScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * §13.1 mechanism 3 — and the reason capture is reliable at all.
 *
 * The broadcast receiver is an optimisation that OEM battery managers routinely kill. This
 * catches everything missed while the process was dead, the phone was rebooting, or the app was
 * force-stopped. If only one mechanism could survive, it would be this one.
 */
@HiltWorker
class PeriodicReconcileWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val captureRepository: CaptureRepository,
    private val dao: CallSessionDao,
    private val notifier: ReviewNotifier,
    private val syncScheduler: SyncScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val result = captureRepository.scan()

        if (result.created > 0) {
            Timber.i("Reconcile picked up %d call(s) the receiver missed", result.created)
            syncScheduler.syncNow()
            notifier.notifyCapture(
                newCount = result.created,
                totalUnreviewed = dao.observeUnreviewedCount().first(),
                latestLabel = null,
            )
        }

        Result.success()
    } catch (e: Exception) {
        Timber.e(e, "Reconcile scan failed")
        Result.success() // Never surface a failure; the next period will try again.
    }

    companion object {
        const val NAME = "callpulse_periodic_reconcile"
    }
}
