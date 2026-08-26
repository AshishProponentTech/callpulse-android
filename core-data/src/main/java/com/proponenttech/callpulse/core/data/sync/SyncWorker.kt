package com.proponenttech.callpulse.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.data.db.SyncStatus
import com.proponenttech.callpulse.core.data.device.DeviceRepository
import com.proponenttech.callpulse.core.data.session.SecureStore
import com.proponenttech.callpulse.core.network.ApiCall
import com.proponenttech.callpulse.core.network.ApiResult
import com.proponenttech.callpulse.core.network.CallPulseApi
import com.proponenttech.callpulse.core.network.dto.BatchRequest
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

/**
 * §15 — pushes local drafts to the server.
 *
 * Order matters: heartbeat first, because it is cheap and it also validates the token, then the
 * batch push.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: CallPulseApi,
    private val apiCall: ApiCall,
    private val dao: CallSessionDao,
    private val secureStore: SecureStore,
    private val deviceRepository: DeviceRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (secureStore.token == null) {
            Timber.d("Not signed in - nothing to sync")
            return Result.success()
        }

        // A previous run may have died mid-flight, leaving rows stuck in the syncing state.
        dao.resetStuckSyncing()

        when (val heartbeat = deviceRepository.sendHeartbeat()) {
            is ApiResult.Success -> if (heartbeat.value.revoked) {
                Timber.w("Device revoked - stopping sync")
                return Result.success()
            }

            ApiResult.Unauthenticated -> {
                // §11.2 — drafts survive; only the session is gone.
                Timber.w("401 during heartbeat - leaving drafts intact")
                return Result.success()
            }

            else -> Timber.d("Heartbeat failed; attempting the push anyway")
        }

        val pending = dao.pendingForSync(CHUNK_SIZE)
        if (pending.isEmpty()) return Result.success()

        val payloads = pending.mapNotNull { CallSessionMapper.toPayload(it, secureStore.deviceUuid) }
        if (payloads.isEmpty()) {
            Timber.d("%d draft(s) still waiting on a project before they can sync", pending.size)
            return Result.success()
        }

        return when (val result = apiCall.execute { api.ingestBatch(BatchRequest(payloads)) }) {
            is ApiResult.Success -> {
                var synced = 0
                var rejected = 0

                result.value.data.results.forEach { row ->
                    when (row.status) {
                        // created, updated and duplicate all mean the server holds the row.
                        "created", "updated", "duplicate" -> {
                            dao.markSynced(row.clientUuid, SyncStatus.SYNCED, row.id)
                            synced++
                        }

                        "rejected" -> {
                            /*
                             * §15 — a validation failure is surfaced, never retried forever. A
                             * row the server will never accept has to become visible to the
                             * employee instead of silently rotting in the queue.
                             */
                            val message = row.errors?.values?.firstOrNull()?.firstOrNull()
                                ?: "The server rejected this call."
                            dao.markFailed(row.clientUuid, message)
                            rejected++
                        }
                    }
                }

                Timber.i("Sync: %d accepted, %d rejected", synced, rejected)
                Result.success()
            }

            ApiResult.Unauthenticated -> Result.success() // Drafts kept; the user re-logs in.
            is ApiResult.NetworkError -> Result.retry()
            is ApiResult.RateLimited -> Result.retry()
            else -> {
                Timber.w("Sync failed: %s", result)
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
            }
        }
    }

    companion object {
        const val NAME = "callpulse_sync"
        private const val CHUNK_SIZE = 100
        private const val MAX_ATTEMPTS = 5
    }
}
