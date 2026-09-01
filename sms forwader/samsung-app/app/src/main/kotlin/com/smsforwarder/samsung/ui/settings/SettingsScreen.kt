package com.smsforwarder.samsung.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings screen for Samsung app.
 * Phase 2 skeleton — full content in Phase 6/8.
 * Will include: notification mode, history retention, key management.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    var notificationMode by remember { mutableStateOf("Full message") }

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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "NOTIFICATIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ListItem(
                headlineContent = { Text("Notification Mode") },
                supportingContent = { Text(notificationMode) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) }
            )
            HorizontalDivider()

            Spacer(Modifier.height(8.dp))
            Text(
                "SECURITY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ListItem(
                headlineContent = { Text("Encryption Keys") },
                supportingContent = { Text("RSA-2048 · Android Keystore") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) }
            )
            ListItem(
                headlineContent = { Text("Rotate Keys") },
                supportingContent = { Text("Generate new key pair and re-pair (Phase 8)") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) }
            )
            HorizontalDivider()

            Spacer(Modifier.height(8.dp))
            Text(
                "HISTORY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ListItem(
                headlineContent = { Text("Retention Period") },
                supportingContent = { Text("Keep messages for 90 days (configurable)") },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) }
            )
            ListItem(
                headlineContent = { Text("Clear History") },
                supportingContent = { Text("Delete all locally stored messages") }
            )
            HorizontalDivider()

            Spacer(Modifier.height(8.dp))
            Text(
                "ABOUT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ListItem(
                headlineContent = { Text("Version") },
                supportingContent = { Text("1.0.0 — Phase 2 (Scaffold)") }
            )
            ListItem(
                headlineContent = { Text("Protocol Version") },
                supportingContent = { Text("v1 — AES-256-GCM + RSA-OAEP-2048") }
            )
        }
    }
}
