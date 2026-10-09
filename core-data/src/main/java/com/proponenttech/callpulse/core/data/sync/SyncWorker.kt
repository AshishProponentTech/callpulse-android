package com.proponenttech.callpulse.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.proponenttech.callpulse.core.data.db.*
import com.proponenttech.callpulse.core.data.device.DeviceRepository
import com.proponenttech.callpulse.core.data.session.*
import com.proponenttech.callpulse.core.network.*
import com.proponenttech.callpulse.core.network.dto.BatchRequest
import dagger.assisted.*
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val api: CallPulseApi,
    private val apiCall: ApiCall,
    private val dao: CallSessionDao,
    private val personalNumbers: PersonalNumberDao,
    private val secureStore: SecureStore,
    private val preferences: AppPreferences,
    private val deviceRepository: DeviceRepository,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        if (secureStore.token == null) return Result.success()
        when (deviceRepository.sendHeartbeat()) {
            ApiResult.Unauthenticated, ApiResult.DeviceRevoked -> return Result.success()
            is ApiResult.NetworkError, is ApiResult.RateLimited -> return Result.retry()
            else -> Unit
        }
        if (preferences.consentAcceptedAt.first() == null) return Result.success()
        dao.resetStuckSyncing()
        repeat(MAX_CHUNKS) {
            if (secureStore.token == null) return Result.success()
            val pending = dao.pendingForSync(CHUNK_SIZE)
            if (pending.isEmpty()) return Result.success()
            val payloads = pending.mapNotNull { call ->
                val personal = call.phoneE164?.let { personalNumbers.contains(it) } ?: false
                val reason = CallSessionMapper.skipReason(call, personal)
                if (reason != null) {
                    dao.markSkipped(call.clientUuid, reason)
                    null
                } else CallSessionMapper.toPayload(call)
            }
            if (payloads.isEmpty()) return@repeat
            when (val result = apiCall.execute { api.ingestBatch(BatchRequest(payloads)) }) {
                is ApiResult.Success -> {
                    var changed = 0
                    var accepted = 0
                    var rejected = 0
                    val sent = payloads.map { it.clientUuid }.toSet()
                    result.value.data.results.forEach { row ->
                        val uuid = row.clientUuid ?: return@forEach
                        if (uuid !in sent) return@forEach
                        when (row.status) {
                            "accepted", "duplicate" -> {
                                dao.markSynced(uuid, row.id, row.lead?.id, row.lead?.name,
                                    row.lead?.status, if (row.lead != null) "linked" else "unreviewed")
                                accepted++; changed++
                            }
                            "rejected" -> {
                                dao.markFailed(uuid, when (row.code) {
                                    "school_unavailable" -> "School is no longer available for uploads."
                                    "client_uuid_unavailable" -> "This call identifier belongs to another device."
                                    "invalid_call" -> "Call details are invalid."
                                    else -> "The server rejected this call."
                                })
                                rejected++; changed++
                            }
                        }
                    }
                    Timber.i("Sync: %d accepted, %d rejected", accepted, rejected)
                    if (changed == 0) return Result.success()
                }
                ApiResult.ConsentRequired -> {
                    preferences.clearConsent()
                    return Result.success()
                }
                ApiResult.Unauthenticated, ApiResult.DeviceRevoked -> return Result.success()
                is ApiResult.NetworkError, is ApiResult.RateLimited -> return Result.retry()
                else -> {
                    Timber.w("Sync: upload failed: %s", result)
                    return if (runAttemptCount < 5) Result.retry() else Result.success()
                }
            }
        }
        return Result.retry() // Continue on another run if the safety cap was reached.
    }
    companion object {
        const val NAME = "callpulse_sync"
        private const val CHUNK_SIZE = 100
        private const val MAX_CHUNKS = 50
    }
}