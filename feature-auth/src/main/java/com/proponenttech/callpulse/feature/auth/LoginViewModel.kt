package com.proponenttech.callpulse.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proponenttech.callpulse.core.data.session.SessionRepository
import com.proponenttech.callpulse.core.network.ApiConfig
import com.proponenttech.callpulse.core.network.ApiResult
import com.proponenttech.callpulse.core.network.dto.ProjectDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isSubmitting: Boolean = false,
    /** What the app is currently doing, so a slow network never looks like a frozen screen. */
    val progressLabel: String? = null,
    val error: String? = null,
    val signedIn: Boolean = false,
    val projects: List<ProjectDto> = emptyList(),
    val workdaySeconds: Int = 28800,
    val consentAccepted: Boolean = false,
    /** Debug builds only — shows which server is being contacted. */
    val serverUrl: String? = null,
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val session: SessionRepository,
    private val config: ApiConfig,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(serverUrl = if (config.isDebug) config.baseUrl else null),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update {
            it.copy(isSubmitting = true, error = null, progressLabel = "Signing in…")
        }

        viewModelScope.launch {
            when (val result = session.login(current.email.trim(), current.password)) {
                is ApiResult.Success -> loadBootstrap()
                else -> _state.update {
                    it.copy(isSubmitting = false, progressLabel = null, error = result.toMessage())
                }
            }
        }
    }

    /** §11.1 — login is not complete until bootstrap has cached the config the app needs. */
    private suspend fun loadBootstrap() {
        // A second round trip follows the login, so say so rather than sitting on "Signing in".
        _state.update { it.copy(progressLabel = "Loading your projects…") }

        when (val result = session.bootstrap()) {
            is ApiResult.Success -> {
                val data = result.value
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        progressLabel = null,
                        signedIn = true,
                        projects = data.projects,
                        workdaySeconds = data.workdaySeconds,
                        consentAccepted = data.consent.acceptedAt != null,
                        password = "", // Never keep it in memory past the point it is needed.
                    )
                }
            }

            else -> {
                Timber.w("Bootstrap failed after a successful login")
                _state.update {
                    it.copy(isSubmitting = false, progressLabel = null, error = result.toMessage())
                }
            }
        }
    }

    fun selectProject(projectId: Long) {
        viewModelScope.launch { session.setDefaultProject(projectId) }
    }

    /**
     * Appendix B — each documented code gets its own message. A generic "something went wrong"
     * for a 409 consent_required is a support ticket waiting to happen.
     */
    private fun ApiResult<*>.toMessage(): String = when (this) {
        is ApiResult.Success -> ""
        ApiResult.Unauthenticated -> "Those credentials were not accepted."
        ApiResult.DeviceRevoked -> "Access for this device has been revoked. Contact your administrator."
        ApiResult.ConsentRequired -> "You need to accept the consent policy before continuing."
        ApiResult.UpdateRequired -> "This version of CallPulse is no longer supported. Please update."
        is ApiResult.ValidationFailed -> errors.values.firstOrNull()?.firstOrNull() ?: message
        is ApiResult.RateLimited -> retryAfterSeconds
            ?.let { "Too many attempts. Try again in $it seconds." }
            ?: "Too many attempts. Try again shortly."

        is ApiResult.HttpError -> message
        is ApiResult.NetworkError -> "Cannot reach the server. Check your connection."
    }
}
