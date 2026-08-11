package com.proponenttech.callpulse.feature.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stage G's landing screen: proves login and bootstrap worked by showing what the server
 * actually returned, and carries the §11.4 sticky-project chip.
 *
 * The day timeline that eventually lives here is Stage L (§16).
 */
@Composable
fun HomeRoute(
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
        ) {
            // §11.4 — "Working on: {Project}" on every screen; tap to change.
            AssistChip(
                onClick = { },
                label = {
                    Text(
                        state.selectedProject?.name?.let { "Working on: $it" }
                            ?: "Choose a project",
                    )
                },
            )

            Text(
                text = "Signed in",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Workday ${state.workdaySeconds / 3600}h · " +
                    "consent ${if (state.consentAccepted) "accepted" else "not accepted"}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )

            Text("Your projects", style = MaterialTheme.typography.titleMedium)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.projects, key = { it.id }) { project ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectProject(project.id) },
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(project.name, style = MaterialTheme.typography.bodyLarge)
                            project.clientName?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            TextButton(onClick = { viewModel.signOut(onSignOut) }) {
                Text("Sign out")
            }
        }
    }
}
