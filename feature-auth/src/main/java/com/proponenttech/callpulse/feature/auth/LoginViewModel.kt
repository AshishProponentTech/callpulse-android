package com.proponenttech.callpulse.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proponenttech.callpulse.core.data.session.DevSettings
import com.proponenttech.callpulse.core.data.session.SessionRepository
import com.proponenttech.callpulse.core.network.ApiConfig
import com.proponenttech.callpulse.core.network.ApiResult
import com.proponenttech.callpulse.core.network.dto.SchoolDto
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
    val schools: List<SchoolDto> = emptyList(),
    val consentAccepted: Boolean = false,
    /** Debug builds only — shows which server is being contacted. */
    val serverUrl: String? = null,
    val sessionReason: String? = null,
    val versionLabel: String = "",
) {
    val canSubmit: Boolean get() = email.isNotBlank() && password.isNotBlank() && !isSubmitting
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val session: SessionRepository,
    private val config: ApiConfig,
    private val devSettings: DevSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(
            versionLabel = "Version ${config.appVersion} · Build ${config.appVersionCode}",
            serverUrl = if (config.isDebug) {
                devSettings.baseUrlOverride ?: config.baseUrl
            } else {
                null
            },
        ),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            session.state.reason.collect { reason -> _state.update { it.copy(sessionReason = reason) } }
        }
    }

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
        _state.update { it.copy(progressLabel = "Loading your schools…") }

        when (val result = session.bootstrap()) {
            is ApiResult.Success -> {
                val data = result.value
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        progressLabel = null,
                        signedIn = true,
                        schools = data.schools,
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

    /**
     * Debug builds only. The staging machine's LAN address is DHCP-assigned and moves; this
     * lets it be corrected on the device rather than rebuilding the APK.
     */
    fun onServerUrlChange(value: String) {
        devSettings.baseUrlOverride = value
        _state.update {
            it.copy(serverUrl = devSettings.baseUrlOverride ?: config.baseUrl, error = null)
        }
    }

    fun selectSchool(schoolId: String) {
        viewModelScope.launch { session.setSchool(schoolId) }
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
        ApiResult.UpdateRequired -> "This version of YogaTribeX CallPulse is no longer supported. Please update."
        is ApiResult.ValidationFailed -> errors.values.firstOrNull()?.firstOrNull() ?: message
        is ApiResult.RateLimited -> retryAfterSeconds
            ?.let { "Too many attempts. Try again in $it seconds." }
            ?: "Too many attempts. Try again shortly."

        is ApiResult.HttpError -> message
        is ApiResult.NetworkError -> "Cannot reach the server. Check your connection."
    }
}
