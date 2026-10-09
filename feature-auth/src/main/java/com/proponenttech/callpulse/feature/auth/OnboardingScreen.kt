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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Before we start", style = MaterialTheme.typography.headlineSmall)

            // ---- Step 1: consent -----------------------------------------------------------
            Card(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
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
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
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
            Card(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
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
                            Column {
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
                    .padding(top = 24.dp),
            ) {
                Text(
                    if (state.captureReady) {
                        "Start tracking calls automatically"
                    } else {
                        "Complete the steps above"
                    },
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(label: String, detail: String, granted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (granted) "Granted" else "Not granted",
            style = MaterialTheme.typography.labelMedium,
            color = if (granted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
        )
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
