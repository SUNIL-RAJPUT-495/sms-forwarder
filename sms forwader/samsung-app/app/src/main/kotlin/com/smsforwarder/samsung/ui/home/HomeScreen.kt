package com.smsforwarder.samsung.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Samsung (Destination) app Home screen.
 *
 * Displays:
 * - Pairing status (paired source device: OPPO)
 * - Last received message timestamp
 * - Total messages received count
 * - Encryption status
 * - Quick navigation to History, Pairing, Settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadPairingState()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("SMS Forwarder", style = MaterialTheme.typography.titleLarge)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── Destination Badge ──────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Destination Device",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Samsung",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Status orb
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(52.dp)
                                .scale(if (uiState.isPaired) pulseScale else 1f)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.isPaired)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else
                                        MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                )
                                .border(
                                    2.dp,
                                    if (uiState.isPaired) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.error,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                if (uiState.isPaired) Icons.Filled.Link else Icons.Filled.LinkOff,
                                contentDescription = null,
                                tint = if (uiState.isPaired) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (uiState.isPaired) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SenderChip("OPPO")
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.ArrowForward, null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.weight(1f).wrapContentWidth()
                            )
                            Spacer(Modifier.width(8.dp))
                            SenderChip("Samsung")
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Paired with: ${uiState.sourceDeviceName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Not paired — tap Pair Device to begin",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Received", uiState.totalReceived.toString(), Icons.Outlined.Inbox)
                        VerticalDivider(Modifier.height(40.dp))
                        StatItem("Encrypted", "E2E", Icons.Outlined.Lock)
                        VerticalDivider(Modifier.height(40.dp))
                        StatItem(
                            "Last",
                            if (uiState.lastReceivedAt != null) "Today" else "—",
                            Icons.Outlined.AccessTime
                        )
                    }
                }
            }

            // ─── Actions ──────────────────────────────────────────
            Text(
                "ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp
            )

            SamsungActionCard(
                icon = Icons.Outlined.Inbox,
                title = "Message History",
                subtitle = "${uiState.totalReceived} bank SMS messages received",
                buttonText = "View All",
                buttonId = "btn_history",
                onClick = onNavigateToHistory,
                isPrimary = true
            )

            if (!uiState.isPaired) {
                SamsungActionCard(
                    icon = Icons.Outlined.QrCode,
                    title = "Pair with OPPO",
                    subtitle = "Generate pairing code — OPPO enters it to begin forwarding",
                    buttonText = "Start Pairing",
                    buttonId = "btn_pair",
                    onClick = onNavigateToPairing,
                    isPrimary = true
                )
            } else {
                SamsungActionCard(
                    icon = Icons.Outlined.LinkOff,
                    title = "Unpair OPPO",
                    subtitle = "Stop forwarding from ${uiState.sourceDeviceName}",
                    buttonText = "Unpair",
                    buttonId = "btn_unpair",
                    onClick = viewModel::unpair,
                    isPrimary = false
                )
            }

            SamsungActionCard(
                icon = Icons.Outlined.BugReport,
                title = "Test Connection",
                subtitle = "Send a test ping to verify the pipeline end-to-end",
                buttonText = "Test",
                buttonId = "btn_test",
                onClick = { /* Phase 9 */ },
                isPrimary = false
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SenderChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SamsungActionCard(
    icon: ImageVector, title: String, subtitle: String,
    buttonText: String, buttonId: String, onClick: () -> Unit, isPrimary: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2
                )
            }
            Spacer(Modifier.width(8.dp))
            if (isPrimary) {
                Button(onClick = onClick, modifier = Modifier.semanticsId(buttonId)) {
                    Text(buttonText, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(onClick = onClick, modifier = Modifier.semanticsId(buttonId)) {
                    Text(buttonText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun Modifier.semanticsId(id: String) = this.semantics {
    testTag = id
}
