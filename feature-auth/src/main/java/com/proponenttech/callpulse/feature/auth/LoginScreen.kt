package com.proponenttech.callpulse.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proponenttech.callpulse.core.ui.theme.*

@Composable
fun LoginRoute(onSignedIn: () -> Unit, viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }
    LoginScreen(state, viewModel::onEmailChange, viewModel::onPasswordChange,
        viewModel::onServerUrlChange, viewModel::submit)
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onServerUrlChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var developerExpanded by remember { mutableStateOf(false) }
    BrandBackground(modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.Transparent) { insets ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(insets).imePadding()) {
                val gutter = if (maxWidth < 360.dp) 16.dp else 24.dp
                val headerSpace = if (maxHeight > 720.dp) 24.dp else 0.dp
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal = gutter, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Spacer(Modifier.height(headerSpace))
                    BrandMark()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("YogaTribeX", style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold)
                        Text("CallPulse", style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary)
                        Text("Every conversation, connected.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                    Card(
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column {
                                Text("Welcome back", style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold)
                                Text("Sign in with your CRM work account",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp))
                            }
                            OutlinedTextField(
                                value = state.email, onValueChange = onEmailChange,
                                label = { Text("Email address") }, singleLine = true,
                                enabled = !state.isSubmitting,
                                leadingIcon = { Icon(Icons.Outlined.MailOutline, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = state.password, onValueChange = onPasswordChange,
                                label = { Text("Password") }, singleLine = true,
                                enabled = !state.isSubmitting,
                                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                            contentDescription = if (passwordVisible) "Hide password" else "Show password")
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onSubmit() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            (state.error ?: state.sessionReason)?.let {
                                Text(it, color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(onClick = onSubmit, enabled = state.canSubmit,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                                if (state.isSubmitting) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary)
                                    Spacer(Modifier.width(12.dp))
                                }
                                Text(if (state.isSubmitting) state.progressLabel ?: "Working…" else "Sign in",
                                    style = MaterialTheme.typography.titleSmall)
                            }
                            if (state.isSubmitting) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                state.serverUrl?.let {
                                    Text("Contacting $it", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    StatusPill(state.versionLabel)
                    if (!state.isSubmitting && state.serverUrl != null) {
                        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                            TextButton(onClick = { developerExpanded = !developerExpanded },
                                modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Icon(Icons.Outlined.Settings, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Developer settings")
                                Icon(if (developerExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                            }
                            if (developerExpanded) {
                                var serverField by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
                                OutlinedTextField(value = serverField, onValueChange = { serverField = it },
                                    label = { Text("Server address") }, singleLine = true,
                                    supportingText = { Text("e.g. 127.0.0.1:8123 — tap Apply after editing") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                    modifier = Modifier.fillMaxWidth())
                                TextButton(onClick = { onServerUrlChange(serverField) }, modifier = Modifier.align(Alignment.End)) {
                                    Text("Apply server address")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
