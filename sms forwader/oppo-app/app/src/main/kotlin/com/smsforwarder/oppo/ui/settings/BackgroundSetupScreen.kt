package com.smsforwarder.oppo.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService

/**
 * Background Setup Guide screen.
 *
 * OPPO/ColorOS aggressively kills background apps. This screen guides
 * the user through the manual settings required for reliable SMS reception.
 *
 * These settings CANNOT be changed programmatically — only the user can
 * apply them via the system settings UI. We provide deep-link buttons
 * wherever Android APIs allow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundSetupScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current

    val powerManager = context.getSystemService<PowerManager>()
    val isIgnoringBatteryOpts = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Background Setup", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "ColorOS restricts background apps to save battery. " +
                               "These settings are required for reliable SMS forwarding when " +
                               "the screen is off or the app is not in the foreground.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = "REQUIRED STEPS",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Step 1 — Battery Optimization
            SetupStep(
                number = "1",
                title = "Disable Battery Optimization",
                description = "Settings → Battery → Battery Optimization → " +
                              "Search for \"SMS Forwarder\" → Select \"Don't optimize\"",
                isComplete = isIgnoringBatteryOpts,
                actionLabel = "Open Battery Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            // Step 2 — Auto-start
            SetupStep(
                number = "2",
                title = "Enable Auto-start",
                description = "Settings → Privacy/Security → Startup Manager (or App Management) → " +
                              "Find \"SMS Forwarder\" → Enable Auto-start.\n\n" +
                              "On some OPPO models this is under: " +
                              "Settings → Battery → App Quick Freeze.",
                isComplete = false, // Cannot be detected programmatically
                actionLabel = "Open App Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            // Step 3 — Keep notification visible
            SetupStep(
                number = "3",
                title = "Keep Foreground Notification",
                description = "Do NOT swipe away the \"SMS Forwarder Running\" notification. " +
                              "This persistent notification keeps the forwarding service alive. " +
                              "Removing it will stop background operation.",
                isComplete = false,
                actionLabel = null,
                onAction = {}
            )

            // Step 4 — Background activity
            SetupStep(
                number = "4",
                title = "Allow Background Activity",
                description = "Settings → App Management → SMS Forwarder → " +
                              "Battery Usage → Select \"Allow background activity\" or " +
                              "\"Unrestricted\".",
                isComplete = false,
                actionLabel = "Open App Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )

            // Step 5 — Network in background
            SetupStep(
                number = "5",
                title = "Allow Background Network Access",
                description = "Settings → Wi-Fi → Advanced → Keep Wi-Fi on during sleep → Always.\n\n" +
                              "Also ensure mobile data background restriction is NOT enabled " +
                              "for this app.",
                isComplete = false,
                actionLabel = null,
                onAction = {}
            )

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "After completing all steps, restart the phone and send a test SMS to verify.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    description: String,
    isComplete: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isComplete)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            // Step number / checkmark
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isComplete)
                    MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isComplete) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Complete",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Text(
                            text = number,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (actionLabel != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(actionLabel, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
