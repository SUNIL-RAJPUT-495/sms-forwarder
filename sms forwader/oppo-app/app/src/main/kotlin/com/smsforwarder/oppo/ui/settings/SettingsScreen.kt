package com.smsforwarder.oppo.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings screen — hub for app configuration.
 * Phase 2: navigation skeleton. Full content in Phase 3 (filter rules) and Phase 8 (security).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToFilterRules: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ListItem(
                headlineContent = { Text("SMS Filter Rules") },
                supportingContent = { Text("Configure which senders and keywords to forward") },
                modifier = Modifier.clickableWithRipple(onNavigateToFilterRules),
                trailingContent = {
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = null
                    )
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Background Execution") },
                supportingContent = { Text("OPPO/ColorOS battery and auto-start settings") },
                trailingContent = {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("App Version") },
                supportingContent = { Text("1.0.0 — Phase 2 (Scaffold)") }
            )
        }
    }
}

private fun Modifier.clickableWithRipple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
