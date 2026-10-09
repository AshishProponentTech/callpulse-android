package com.proponenttech.callpulse.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proponenttech.callpulse.core.data.db.CallSessionEntity
import com.proponenttech.callpulse.core.data.db.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeRoute(
    onSignOut: () -> Unit,
    onOpenOnboarding: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // §11.4 — "Working on: {School}" on every screen; tap to change.
                Box {
                    var pickerOpen by remember { mutableStateOf(false) }

                    AssistChip(
                        onClick = { pickerOpen = true },
                        label = {
                            Text(
                                state.selectedSchool?.name?.let { "Working on: $it" }
                                    ?: "Choose a school",
                            )
                        },
                    )

                    DropdownMenu(
                        expanded = pickerOpen,
                        onDismissRequest = { pickerOpen = false },
                    ) {
                        if (state.schools.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No schools available") },
                                onClick = { pickerOpen = false },
                            )
                        }
                        state.schools.forEach { school ->
                            DropdownMenuItem(
                                text = { Text(school.name) },
                                onClick = {
                                    viewModel.selectSchool(school.id)
                                    pickerOpen = false
                                },
                            )
                        }
                    }
                }
                TextButton(onClick = viewModel::scanNow) { Text("Scan now") }
            }

            /*
             * §12.1 — a persistent banner while a required permission is missing or consent has
             * not been recorded. Capture silently doing nothing is the worst possible failure
             * mode: the employee believes they are being tracked and the manager sees no data.
             */
            if (!viewModel.canCapture) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clickable { onOpenOnboarding() },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = "Call capture is off",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            text = if (!state.consentAccepted) {
                                "Consent has not been recorded. Tap to finish setup."
                            } else if (state.selectedSchool == null) {
                                "Choose a school to start capture."
                            } else {
                                "The call log permission is not granted. Tap to fix."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            Text(
                text = "Captured calls",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "${state.calls.size} captured",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.calls.isEmpty()) {
                // Empty states that say something useful (§7.2).
                Text(
                    text = if (viewModel.canCapture) {
                        "No calls captured yet. Make or receive a call, then pull Scan now."
                    } else {
                        "No calls captured — capture is not running yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            if (state.failedCount > 0) {
                TextButton(onClick = viewModel::retryFailed) { Text("Retry failed uploads") }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.calls, key = { it.id }) { call -> CallRow(call) }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onOpenOnboarding) { Text("Setup") }
                TextButton(onClick = { viewModel.signOut(onSignOut) }) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun CallRow(call: CallSessionEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    // Elvis only falls through on null, so a blank string would render an empty
                    // row - which is exactly what a withheld number produces (§13.4).
                    text = listOfNotNull(call.leadName, call.contactName, call.phoneE164, call.phoneDisplay)
                        .firstOrNull { it.isNotBlank() }
                        ?: "Private number",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = formatDuration(call.durationSec),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Text(
                text = "${call.direction} · ${formatTime(call.startedAtMillis)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // §15 — a rejected row must be visible, with the reason, not silently stuck.
            val syncLabel = when (call.syncStatus) {
                SyncStatus.SYNCED -> "Uploaded"
                SyncStatus.SKIPPED -> call.syncError ?: "Upload skipped"
                SyncStatus.FAILED -> call.syncError ?: "Rejected by the server"
                else -> if (call.schoolId == null) "Choose a school" else "Waiting to upload"
            }

            Text(
                text = syncLabel,
                style = MaterialTheme.typography.labelSmall,
                color = if (call.syncStatus == SyncStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            call.leadStatus?.let { Text("Lead status: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun formatDuration(seconds: Int): String = when {
    seconds <= 0 -> "no answer"
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
