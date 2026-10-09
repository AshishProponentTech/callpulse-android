package com.proponenttech.callpulse.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.data.db.CallSessionEntity
import com.proponenttech.callpulse.core.data.device.PermissionState
import com.proponenttech.callpulse.core.data.session.AppPreferences
import com.proponenttech.callpulse.core.data.session.SessionRepository
import com.proponenttech.callpulse.core.data.sync.SyncScheduler
import com.proponenttech.callpulse.core.network.dto.SchoolDto
import com.proponenttech.callpulse.feature.capture.CaptureScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val schools: List<SchoolDto> = emptyList(),
    val selectedSchool: SchoolDto? = null,
    val failedCount: Int = 0,
    val consentAccepted: Boolean = false,
    val callLogGranted: Boolean = false,
    val calls: List<CallSessionEntity> = emptyList(),
    val unreviewedCount: Int = 0,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: SessionRepository,
    private val preferences: AppPreferences,
    private val dao: CallSessionDao,
    private val captureScheduler: CaptureScheduler,
    private val syncScheduler: SyncScheduler,
    private val permissions: PermissionState,
) : ViewModel() {

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning
    private val callLogGranted = MutableStateFlow(permissions.callLog)

    val state: StateFlow<HomeUiState> = combine(
        session.cachedSchools,
        session.selectedSchoolId,
        dao.observeFailedCount(),
        preferences.consentAcceptedAt,
        dao.observeRecent(),
    ) { schools, defaultId, failedCount, consentAcceptedAt, calls ->
        HomeUiState(
            schools = schools,
            selectedSchool = schools.firstOrNull { it.id == defaultId },
            failedCount = failedCount,
            consentAccepted = consentAcceptedAt != null,
            calls = calls,
            unreviewedCount = calls.count { it.reviewedAtMillis == null },
        )
    }.combine(callLogGranted) { state, granted -> state.copy(callLogGranted = granted) }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    val canCapture: Boolean get() = state.value.callLogGranted && state.value.consentAccepted && state.value.selectedSchool != null

    /** §13.1 — a scan also runs when the app comes to the foreground. */
    fun onResume() {
        callLogGranted.value = permissions.callLog
        viewModelScope.launch {
            when (session.refreshIfExpiring()) {
                com.proponenttech.callpulse.core.network.ApiResult.Unauthenticated,
                com.proponenttech.callpulse.core.network.ApiResult.DeviceRevoked -> return@launch
                else -> Unit
            }
            session.bootstrap()
            if (session.isLoggedIn) {
                captureScheduler.ensurePeriodicReconcile()
                captureScheduler.scanNow()
                syncScheduler.ensurePeriodicSync()
                syncScheduler.ensurePeriodicHeartbeat()
                syncScheduler.syncNow()
            }
        }

        /*
         * Re-arm the periodic upload and heartbeat too, not just the periodic capture.
         *
         * These were armed once during onboarding and never again, so when an OEM battery
         * manager or a force-stop dropped the queued work, nothing restored it — leaving
         * syncNow() below as the only surviving upload path, which is why data appeared only
         * when someone opened the app. KEEP means re-arming an existing schedule is a no-op.
         */
    }

    fun retryFailed() {
        viewModelScope.launch {
            dao.retryFailed()
            syncScheduler.syncNow()
        }
    }

    fun scanNow() {
        viewModelScope.launch {
            _scanning.value = true
            captureScheduler.scanNow()
            syncScheduler.syncNow()
            _scanning.value = false
        }
    }

    /** §11.4 — affects future calls only; historical drafts are never rewritten. */
    fun selectSchool(schoolId: String) {
        viewModelScope.launch { session.setSchool(schoolId) }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            captureScheduler.cancelAll()
            session.logout()
            onDone()
        }
    }
}
