package com.proponenttech.callpulse.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proponenttech.callpulse.core.data.db.CallSessionDao
import com.proponenttech.callpulse.core.data.db.CallSessionEntity
import com.proponenttech.callpulse.core.data.device.PermissionState
import com.proponenttech.callpulse.core.data.session.AppPreferences
import com.proponenttech.callpulse.core.data.session.SessionRepository
import com.proponenttech.callpulse.core.data.sync.SyncScheduler
import com.proponenttech.callpulse.core.network.dto.ProjectDto
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
    val projects: List<ProjectDto> = emptyList(),
    val selectedProject: ProjectDto? = null,
    val workdaySeconds: Int = 28800,
    val consentAccepted: Boolean = false,
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

    val state: StateFlow<HomeUiState> = combine(
        session.cachedProjects,
        session.defaultProjectId,
        preferences.workdaySeconds,
        preferences.consentAcceptedAt,
        dao.observeRecent(),
    ) { projects, defaultId, workday, consentAcceptedAt, calls ->
        HomeUiState(
            projects = projects,
            selectedProject = projects.firstOrNull { it.id == defaultId },
            workdaySeconds = workday,
            consentAccepted = consentAcceptedAt != null,
            calls = calls,
            unreviewedCount = calls.count { it.reviewedAtMillis == null },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    val canCapture: Boolean get() = permissions.canCapture

    /** §13.1 — a scan also runs when the app comes to the foreground. */
    fun onResume() {
        if (permissions.canCapture) {
            captureScheduler.ensurePeriodicReconcile()
            captureScheduler.scanNow()
        }
        syncScheduler.syncNow()
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
    fun selectProject(projectId: Long) {
        viewModelScope.launch { session.setDefaultProject(projectId) }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            captureScheduler.cancelAll()
            session.logout()
            onDone()
        }
    }
}
