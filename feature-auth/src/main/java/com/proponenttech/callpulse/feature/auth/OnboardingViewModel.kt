package com.proponenttech.callpulse.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proponenttech.callpulse.core.data.device.DeviceRepository
import com.proponenttech.callpulse.core.data.device.PermissionState
import com.proponenttech.callpulse.core.data.session.AppPreferences
import com.proponenttech.callpulse.core.data.session.SessionRepository
import com.proponenttech.callpulse.core.network.dto.ProjectDto
import com.proponenttech.callpulse.core.data.sync.SyncScheduler
import com.proponenttech.callpulse.core.network.ApiResult
import com.proponenttech.callpulse.feature.capture.CaptureScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class OnboardingUiState(
    val consentAccepted: Boolean = false,
    val consentChecked: Boolean = false,
    val isSubmittingConsent: Boolean = false,
    val consentError: String? = null,
    val consentVersion: String = "1.0",
    val callLogGranted: Boolean = false,
    val phoneStateGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val contactsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val projects: List<ProjectDto> = emptyList(),
    val selectedProjectId: Long? = null,
) {
    /**
     * Capture runs hands-off, so all three have to be true before it starts: consent recorded
     * (decision #14), the call log permission (§12), and a project for captured calls to land on
     * (rule 7 - no call may exist without one). Leaving the project unset would capture calls
     * that can never sync.
     */
    val captureReady: Boolean
        get() = consentAccepted && callLogGranted && selectedProjectId != null
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val permissions: PermissionState,
    private val preferences: AppPreferences,
    private val session: SessionRepository,
    private val captureScheduler: CaptureScheduler,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val acceptedAt = preferences.consentAcceptedAt.first()
            val version = preferences.consentVersion.first() ?: "1.0"
            _state.update {
                it.copy(consentAccepted = acceptedAt != null, consentVersion = version)
            }
            refreshPermissions()
        }

        viewModelScope.launch {
            session.cachedProjects.collect { projects ->
                _state.update { it.copy(projects = projects) }
            }
        }

        viewModelScope.launch {
            session.defaultProjectId.collect { id ->
                _state.update { it.copy(selectedProjectId = id) }
                if (_state.value.captureReady) startCapture()
            }
        }
    }

    fun selectProject(projectId: Long) {
        viewModelScope.launch { session.setDefaultProject(projectId) }
    }

    /** Called whenever the screen resumes — permissions can change outside the app. */
    fun refreshPermissions() {
        _state.update {
            it.copy(
                callLogGranted = permissions.callLog,
                phoneStateGranted = permissions.phoneState,
                notificationsGranted = permissions.notifications,
                contactsGranted = permissions.contacts,
                batteryOptimizationIgnored = permissions.batteryOptimizationIgnored,
            )
        }

        // A permission may have just been granted; start capture the moment it is possible.
        if (_state.value.captureReady) startCapture()
    }

    fun onConsentCheckedChange(checked: Boolean) {
        _state.update { it.copy(consentChecked = checked, consentError = null) }
    }

    /**
     * Decision #14 — the consent record is stored server-side, so the API can prove it later.
     * Capture stays disabled until this returns 2xx; a locally-remembered tick is not enough.
     */
    fun acceptConsent() {
        val current = _state.value
        if (!current.consentChecked || current.isSubmittingConsent) return

        _state.update { it.copy(isSubmittingConsent = true, consentError = null) }

        viewModelScope.launch {
            when (val result = deviceRepository.recordConsent(current.consentVersion)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(isSubmittingConsent = false, consentAccepted = true)
                    }
                    refreshPermissions()
                }

                else -> {
                    Timber.w("Consent could not be recorded: %s", result)
                    _state.update {
                        it.copy(
                            isSubmittingConsent = false,
                            consentError = when (result) {
                                is ApiResult.NetworkError ->
                                    "Cannot reach the server. Consent must be recorded before capture can start."

                                ApiResult.Unauthenticated -> "Your session expired. Sign in again."
                                ApiResult.DeviceRevoked -> "Access for this device has been revoked."
                                else -> "Consent could not be recorded. Try again."
                            },
                        )
                    }
                }
            }
        }
    }

    private fun startCapture() {
        captureScheduler.ensurePeriodicReconcile()
        captureScheduler.scanNow()
        syncScheduler.ensurePeriodicSync()
        syncScheduler.ensurePeriodicHeartbeat()
        syncScheduler.heartbeatNow()
    }
}
