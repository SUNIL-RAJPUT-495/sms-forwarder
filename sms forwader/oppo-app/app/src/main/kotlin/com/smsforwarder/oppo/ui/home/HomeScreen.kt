package com.smsforwarder.oppo.ui.home

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main Home screen for the OPPO (Source) app.
 *
 * Displays:
 * - Connection / pairing status
 * - Pending message queue count
 * - Service status
 * - Quick action buttons (Pair, Settings, Background Setup, Test SMS)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToBackgroundSetup: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadPairingState()
    }

    // Pulsing animation for the "Live" status indicator
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "SMS Forwarder",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings, modifier = Modifier.semanticsId("btn_settings")) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── Status Card ─────────────────────────────────────────
            StatusCard(
                isPaired = uiState.isPaired,
                destinationName = uiState.destinationDeviceName,
                pendingCount = uiState.pendingCount,
                pulseScale = pulseScale
            )

            // ─── Battery Warning ─────────────────────────────────────
            AnimatedVisibility(visible = uiState.isBatteryOptimized) {
                WarningBanner(
                    message = "Battery optimization is ON — SMS may not be received reliably in background.",
                    actionLabel = "Fix",
                    onAction = onNavigateToBackgroundSetup
                )
            }

            // ─── Quick Actions ───────────────────────────────────────
            Text(
                text = "QUICK ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (!uiState.isPaired) {
                ActionCard(
                    icon = Icons.Outlined.QrCodeScanner,
                    title = "Pair with Samsung",
                    subtitle = "Connect this device to your Samsung phone to begin forwarding",
                    buttonText = "Start Pairing",
                    buttonId = "btn_pair",
                    onClick = onNavigateToPairing,
                    isPrimary = true
                )
            } else {
                ActionCard(
                    icon = Icons.Outlined.LinkOff,
                    title = "Unpair Device",
                    subtitle = "Remove pairing with ${uiState.destinationDeviceName}",
                    buttonText = "Unpair",
                    buttonId = "btn_unpair",
                    onClick = viewModel::unpair,
                    isPrimary = false
                )
            }

            ActionCard(
                icon = Icons.Outlined.FilterList,
                title = "SMS Filter Rules",
                subtitle = "Configure which senders and keywords to forward",
                buttonText = "Configure",
                buttonId = "btn_filter_rules",
                onClick = onNavigateToSettings,
                isPrimary = false
            )

            ActionCard(
                icon = Icons.Outlined.BatteryAlert,
                title = "Background Setup",
                subtitle = "Required: configure OPPO/ColorOS battery and auto-start settings",
                buttonText = "Open Guide",
                buttonId = "btn_bg_setup",
                onClick = onNavigateToBackgroundSetup,
                isPrimary = false
            )

            // ─── Test SMS ────────────────────────────────────────────
            Divider(modifier = Modifier.padding(vertical = 4.dp))

            TestSmsSection(
                uiState = uiState,
                onSendTest = viewModel::sendTestSms,
                onDismissResult = viewModel::clearTestResult
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(
    isPaired: Boolean,
    destinationName: String,
    pendingCount: Int,
    pulseScale: Float
) {
    val statusColor = if (isPaired) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = "Source Device",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "OPPO",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Status indicator
                Column(horizontalAlignment = Alignment.End) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .scale(if (isPaired) pulseScale else 1f)
                            .clip(CircleShape)
                            .background(statusColor.copy(alpha = 0.15f))
                            .border(2.dp, statusColor, CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPaired) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = if (isPaired) "Paired" else "Not paired",
                            tint = statusColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isPaired) "PAIRED" else "NOT PAIRED",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Connection arrow
            if (isPaired) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Chip(label = "OPPO")
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        Icons.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).wrapContentWidth()
                    )
                    Spacer(Modifier.width(8.dp))
                    Chip(label = destinationName)
                }

                Spacer(Modifier.height(16.dp))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(12.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Pending",
                    value = pendingCount.toString(),
                    icon = Icons.Outlined.Schedule
                )
                VerticalDivider(modifier = Modifier.height(40.dp))
                StatItem(
                    label = "Encrypted",
                    value = "E2E",
                    icon = Icons.Outlined.Lock
                )
                VerticalDivider(modifier = Modifier.height(40.dp))
                StatItem(
                    label = "Protocol",
                    value = "v1",
                    icon = Icons.Outlined.Info
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WarningBanner(
    message: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = "Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    buttonText: String,
    buttonId: String,
    onClick: () -> Unit,
    isPrimary: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }

            Spacer(Modifier.width(8.dp))

            if (isPrimary) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.semanticsId(buttonId)
                ) {
                    Text(buttonText, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(
                    onClick = onClick,
                    modifier = Modifier.semanticsId(buttonId)
                ) {
                    Text(buttonText, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TestSmsSection(
    uiState: HomeUiState,
    onSendTest: () -> Unit,
    onDismissResult: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "TEST MODE",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.5.sp
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Science,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Send Test SMS",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Sends a synthetic bank SMS (TESTBANK → \"OTP: 123456\") through the full pipeline: encrypt → backend → FCM → Samsung → decrypt → notification.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // Result feedback
                AnimatedVisibility(visible = uiState.testSmsResult != null) {
                    uiState.testSmsResult?.let { result ->
                        when (result) {
                            is TestSmsResult.Sending -> {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            is TestSmsResult.Success -> {
                                ResultChip(
                                    text = "✓ Sent — ID: ${result.messageId.take(16)}…",
                                    isSuccess = true,
                                    onDismiss = onDismissResult
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            is TestSmsResult.FilteredOut -> {
                                ResultChip(
                                    text = "⚠ Filtered: ${result.reason}",
                                    isSuccess = false,
                                    isWarning = true,
                                    onDismiss = onDismissResult
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                            is TestSmsResult.Error -> {
                                ResultChip(
                                    text = "✗ Error: ${result.message}",
                                    isSuccess = false,
                                    onDismiss = onDismissResult
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                Button(
                    onClick = onSendTest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semanticsId("btn_test_sms"),
                    enabled = uiState.testSmsResult !is TestSmsResult.Sending,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    )
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send Test SMS")
                }
            }
        }
    }
}

@Composable
private fun ResultChip(
    text: String,
    isSuccess: Boolean,
    isWarning: Boolean = false,
    onDismiss: () -> Unit
) {
    val color = when {
        isSuccess -> MaterialTheme.colorScheme.primary
        isWarning -> MaterialTheme.colorScheme.tertiary
        else      -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = color)
        }
    }
}


// Extension to attach a semantics ID for UI testing
private fun Modifier.semanticsId(id: String): Modifier =
    this.semantics {
        testTag = id
    }
