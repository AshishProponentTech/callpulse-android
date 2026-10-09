package com.proponenttech.callpulse.feature.auth

import android.Manifest
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proponenttech.callpulse.core.ui.theme.BrandBackground
import com.proponenttech.callpulse.core.ui.theme.BrandMark
import com.proponenttech.callpulse.core.ui.theme.StatusPill

/**
 * §12 — consent then permissions, as a stepper that explains WHY before each system dialog.
 *
 * The order is deliberate: consent is recorded first, because the server refuses call data from
 * a device without a consent record (decision #14). Asking for the call log before the employee
 * has agreed to anything would be both rude and useless.
 */
@Composable
fun OnboardingRoute(
    onReady: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /*
     * Returning from the battery-optimisation dialog produces no result, so re-read the
     * permission state whenever this screen resumes. Otherwise the prompt stays on screen after
     * the user has already granted it, and they tap it again wondering why nothing happened.
     */
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refreshPermissions() }

    BrandBackground(Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.Transparent) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 640.dp)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                ) {
                    BrandMark()
                    Text("Make it yours", style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text("Three simple steps to connect your calls to your CRM.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    val completed = listOf(state.consentAccepted, state.callLogGranted, state.selectedSchoolId != null).count { it }
                    LinearProgressIndicator(progress = { completed / 3f }, modifier = Modifier.fillMaxWidth())
                    Text("$completed of 3 steps complete", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))

                    // ---- Step 1: consent -----------------------------------------------------------
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("1. Consent", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "YogatribexCRM records the number, direction, start time and duration " +
                                    "of your calls and uploads them automatically to your school’s CRM.\n\n" +
                                    "It never records audio, never reads your messages, and never " +
                                    "uploads your address book. Numbers on the personal exclusion list " +
                                    "are never uploaded. Unlinked calls are removed by the server after 30 days.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp),
                            )

                            state.policyUrl?.let { url ->
                                OutlinedButton(onClick = {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }) { Text("Read consent policy") }
                            }

                            if (state.consentAccepted) {
                                Text(
                                    text = "Accepted",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(top = 12.dp),
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 12.dp),
                                ) {
                                    Checkbox(
                                        checked = state.consentChecked,
                                        onCheckedChange = viewModel::onConsentCheckedChange,
                                    )
                                    Text("I understand and agree", style = MaterialTheme.typography.bodyMedium)
                                }

                                state.consentError?.let {
                                    Text(
                                        text = it,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }

                                Button(
                                    onClick = viewModel::acceptConsent,
                                    enabled = state.consentVersion != null && state.consentChecked && !state.isSubmittingConsent,
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    if (state.isSubmittingConsent) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Text("Accept")
                                    }
                                }
                            }
                        }
                    }

                    // ---- Step 2: permissions -------------------------------------------------------
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("2. Permissions", style = MaterialTheme.typography.titleMedium)

                            PermissionRow(
                                label = "Call log",
                                detail = "Required — this is how calls are captured",
                                granted = state.callLogGranted,
                            )
                            PermissionRow(
                                label = "Phone state",
                                detail = "Detects when a call ends, so capture is prompt",
                                granted = state.phoneStateGranted,
                            )
                            PermissionRow(
                                label = "Notifications",
                                detail = "Confirms when a call has been captured",
                                granted = state.notificationsGranted,
                            )
                            PermissionRow(
                                label = "Contacts",
                                detail = "Optional — shows a name instead of a bare number",
                                granted = state.contactsGranted,
                            )

                            OutlinedButton(
                                onClick = {
                                    permissionLauncher.launch(requestedPermissions())
                                },
                                enabled = state.consentAccepted,
                                modifier = Modifier.padding(top = 12.dp),
                            ) {
                                Text(if (state.callLogGranted) "Review permissions" else "Grant permissions")
                            }

                            /*
                             * The manifest has always declared REQUEST_IGNORE_BATTERY_OPTIMIZATIONS and
                             * the heartbeat has always reported whether it was granted — but nothing
                             * ever ASKED for it. Under Doze the 15-minute upload is deferred to
                             * whenever the system feels like it, which on many handsets means "when the
                             * user next opens the app". Asking is the single highest-impact thing the
                             * app can do about background reliability.
                             */
                            if (!state.batteryOptimizationIgnored) {
                                OutlinedButton(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(
                                                Intent(
                                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                    Uri.parse("package:" + context.packageName),
                                                )
                                            )
                                        }.onFailure {
                                            // Some OEM builds hide this screen. Fall back to the
                                            // general battery settings list rather than crashing.
                                            runCatching {
                                                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                            }
                                        }
                                    },
                                    modifier = Modifier.padding(top = 8.dp),
                                ) {
                                    Text("Allow background syncing")
                                }

                                Text(
                                    text = "Without this, Android pauses the app and calls only upload " +
                                        "when you next open it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }

                            if (!state.consentAccepted) {
                                Text(
                                    text = "Accept the consent notice first.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    // ---- Step 3: school ------------------------------------------------------------
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("3. Default school", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "Captured calls are logged against this school automatically, " +
                                    "with no action from you. You can change it any time; it only " +
                                    "affects calls made afterwards.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                            )

                            if (state.schools.isEmpty()) {
                                Text(
                                    "No schools available for your account.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }

                            state.schools.forEach { school ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectSchool(school.id) }
                                        .padding(vertical = 4.dp),
                                ) {
                                    RadioButton(
                                        selected = state.selectedSchoolId == school.id,
                                        onClick = { viewModel.selectSchool(school.id) },
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(school.name, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = onReady,
                        enabled = state.captureReady,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                            .heightIn(min = 52.dp),
                    ) {
                        Text(
                            if (state.captureReady) {
                                "Start capturing calls"
                            } else {
                                "Complete the steps above"
                            },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, detail: String, granted: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StatusPill(if (granted) "Granted" else "Not granted", warning = !granted)
    }
}

/**
 * §12 — exactly this set. POST_NOTIFICATIONS only exists as a runtime permission from API 33;
 * requesting it below that throws.
 */
private fun requestedPermissions(): Array<String> = buildList {
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.READ_PHONE_STATE)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}.toTypedArray()
