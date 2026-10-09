package com.proponenttech.callpulse.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proponenttech.callpulse.core.data.db.CallSessionEntity
import com.proponenttech.callpulse.core.data.db.SyncStatus
import com.proponenttech.callpulse.core.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeRoute(onSignOut: () -> Unit, onOpenOnboarding: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BrandBackground(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = onOpenOnboarding) {
                            Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Setup")
                        }
                        TextButton(onClick = { viewModel.signOut(onSignOut) }) { Text("Sign out") }
                    }
                }
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            BrandMark()
                            Column(Modifier.weight(1f)) {
                                Text("YogaTribeX", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("CallPulse", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    item {
                        Column(
                            Modifier.fillMaxWidth().background(
                                Brush.linearGradient(listOf(Color(0xFF4A5DF9), Color(0xFF4152D3))),
                                RoundedCornerShape(24.dp),
                            ).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text("Your calls, connected.", color = Color.White,
                                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                            Text(if (viewModel.canCapture) "Calls upload to your school’s CRM automatically."
                                else "Finish setup to start capturing calls.",
                                color = Color.White, style = MaterialTheme.typography.bodyMedium)
                            SchoolPicker(state, viewModel::selectSchool)
                        }
                    }
                    if (!viewModel.canCapture) {
                        item {
                            Card(onClick = onOpenOnboarding,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Call capture is off", style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer)
                                    Text(if (!state.consentAccepted) "Consent has not been recorded. Tap to finish setup."
                                        else if (state.selectedSchool == null) "Choose a school to start capture."
                                        else "The call log permission is not granted. Tap to fix.",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Captured calls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                                Text("${state.calls.size} recent calls", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = viewModel::scanNow) {
                                Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Scan now")
                            }
                        }
                    }
                    if (state.failedCount > 0) {
                        item { OutlinedButton(onClick = viewModel::retryFailed, modifier = Modifier.fillMaxWidth()) {
                            Text("Retry failed uploads (${state.failedCount})")
                        } }
                    }
                    if (state.calls.isEmpty()) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                                Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Outlined.PhoneInTalk, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
                                    Text("Ready for your first call", style = MaterialTheme.typography.titleMedium)
                                    Text(if (viewModel.canCapture) "Make or receive a call, then tap Scan now."
                                        else "Complete setup to see your calls here.",
                                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    items(state.calls, key = { it.id }) { CallRow(it) }
                }
            }
        }
    }
}

@Composable
private fun SchoolPicker(state: HomeUiState, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(onClick = { expanded = true }, shape = RoundedCornerShape(14.dp),
            color = Color.White, contentColor = Color(0xFF202C78)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.School, null, Modifier.size(22.dp))
                Column(Modifier.weight(1f)) {
                    Text("Working on", style = MaterialTheme.typography.labelSmall)
                    Text(state.selectedSchool?.name ?: "Choose a school", style = MaterialTheme.typography.titleSmall)
                }
                Icon(Icons.Outlined.ExpandMore, "Change school")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (state.schools.isEmpty()) DropdownMenuItem(text = { Text("No schools available") }, onClick = { expanded = false })
            state.schools.forEach { school ->
                DropdownMenuItem(text = { Text(school.name) }, onClick = { onSelect(school.id); expanded = false })
            }
        }
    }
}

@Composable
private fun CallRow(call: CallSessionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Icon(when (call.direction) {
                        "inbound" -> Icons.Outlined.CallReceived
                        "outbound" -> Icons.Outlined.CallMade
                        else -> Icons.Outlined.CallMissed
                    }, null, Modifier.padding(12.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(call.leadName, call.contactName, call.phoneE164, call.phoneDisplay)
                        .firstOrNull { it.isNotBlank() } ?: "Private number",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${call.direction.replaceFirstChar { it.titlecase() }} · ${formatTime(call.startedAtMillis)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(formatDuration(call.durationSec), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val label = when (call.syncStatus) {
                SyncStatus.SYNCED -> "Uploaded"
                SyncStatus.SKIPPED -> call.syncError ?: "Upload skipped"
                SyncStatus.FAILED -> call.syncError ?: "Rejected by the server"
                else -> if (call.schoolId == null) "Choose a school" else "Waiting to upload"
            }
            StatusPill(label, warning = call.syncStatus == SyncStatus.FAILED)
            call.leadStatus?.let { Text("Lead status: $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun formatDuration(seconds: Int): String = when {
    seconds <= 0 -> "No answer"
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}m ${seconds % 60}s"
}

private fun formatTime(millis: Long): String = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(millis))
