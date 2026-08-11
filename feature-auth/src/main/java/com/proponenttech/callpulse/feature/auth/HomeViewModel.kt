package com.proponenttech.callpulse.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proponenttech.callpulse.core.data.session.AppPreferences
import com.proponenttech.callpulse.core.data.session.SessionRepository
import com.proponenttech.callpulse.core.network.dto.ProjectDto
import dagger.hilt.android.lifecycle.HiltViewModel
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
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: SessionRepository,
    private val preferences: AppPreferences,
) : ViewModel() {

    val state: StateFlow<HomeUiState> = combine(
        session.cachedProjects,
        session.defaultProjectId,
        preferences.workdaySeconds,
        preferences.consentAcceptedAt,
    ) { projects, defaultId, workday, consentAcceptedAt ->
        HomeUiState(
            projects = projects,
            selectedProject = projects.firstOrNull { it.id == defaultId },
            workdaySeconds = workday,
            consentAccepted = consentAcceptedAt != null,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    /** §11.4 — affects future calls only; historical drafts are never rewritten. */
    fun selectProject(projectId: Long) {
        viewModelScope.launch { session.setDefaultProject(projectId) }
    }

    fun signOut(onDone: () -> Unit) {
        viewModelScope.launch {
            session.logout()
            onDone()
        }
    }
}
