package com.smsforwarder.app.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsforwarder.app.domain.model.DeviceInfo
import com.smsforwarder.app.domain.model.DeviceRole
import com.smsforwarder.app.ui.theme.AccentGreen
import com.smsforwarder.app.ui.theme.PrimaryBlue
import com.smsforwarder.app.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigatePairing: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateFilters: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBatteryGuide: () -> Unit,
    onNavigateModeSelection: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val deviceInfo = state.deviceInfo ?: return

    // For Sender / Forwarder phone: Show simplified Form or "It is registered" screen
    if (deviceInfo.role == DeviceRole.SENDER) {
        if (!deviceInfo.isRegistered) {
            SenderFormScreen(
                isRegistering = state.isRegistering,
                errorMessage = state.errorMessage,
                defaultName = deviceInfo.deviceName,
                onRegister = { name, mobile, address ->
                    viewModel.registerSenderDevice(name, mobile, address)
                }
            )
        } else {
            SenderRegisteredScreen(info = deviceInfo)
        }
    } else {
        // Full dashboard for Receiver / Dual client phone
        FullDashboardScreen(
            state = state,
            viewModel = viewModel,
            onNavigatePairing = onNavigatePairing,
            onNavigateHistory = onNavigateHistory,
            onNavigateFilters = onNavigateFilters,
            onNavigateSettings = onNavigateSettings,
            onNavigateBatteryGuide = onNavigateBatteryGuide,
            onNavigateModeSelection = onNavigateModeSelection
        )
    }
}

/**
 * Clean Form Screen for Sender / Forwarding phone registration.
 */
@Composable
private fun SenderFormScreen(
    isRegistering: Boolean,
    errorMessage: String?,
    defaultName: String,
    onRegister: (name: String, mobile: String, address: String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var mobileNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SendToMobile,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    text = "SMS Forwarder Setup",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Fill in your details to register this phone for automatic SMS forwarding.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name / Department") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = mobileNumber,
                    onValueChange = { mobileNumber = it },
                    label = { Text("Mobile Number") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                errorMessage?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onRegister(name, mobileNumber, address) },
                    enabled = !isRegistering && name.isNotBlank() && mobileNumber.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Registering...")
                    } else {
                        Text("Register Device", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Clean Screen shown after Sender Phone is registered: "It is registered" (No other options).
 */
@Composable
private fun SenderRegisteredScreen(info: DeviceInfo) {
    val context = LocalContext.current
    var isNotificationListenerEnabled by remember {
        mutableStateOf(checkNotificationListenerEnabled(context))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = AccentGreen.copy(alpha = 0.15f),
                modifier = Modifier.size(110.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(70.dp)
                    )
                }
            }

            Text(
                text = "It is registered",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Text(
                text = "SMS Forwarder service is active in background",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    InfoRow(label = "Name / Dept", value = info.departmentName.ifBlank { info.deviceName })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    InfoRow(label = "Mobile No", value = info.mobileNumber.ifBlank { "N/A" })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    InfoRow(label = "Address", value = info.address.ifBlank { "Main Office" })
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "● Active & Forwarding",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                    }
                }
            }

            // STEALTH & DISGUISE BUTTONS (Appears only after Registration)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { hideAppStealth(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(Icons.Default.VisibilityOff, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Hide Icon Completely (Vanish Mode)", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { disguiseAsCalculator(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Calculate, contentDescription = null, tint = AccentGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Disguise App as Calculator Icon", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (!isNotificationListenerEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = WarningAmber)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Notification Access Required", fontWeight = FontWeight.Bold, color = WarningAmber)
                        }
                        Text(
                            "To capture bank alerts & OTPs, please enable Notification Access permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = WarningAmber)
                        ) {
                            Text("Enable Notification Access Permission", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = AccentGreen.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification Access Enabled",
                            color = AccentGreen,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun checkNotificationListenerEnabled(context: Context): Boolean {
    val pkgName = context.packageName
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(pkgName)
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Full Dashboard View for Receiver / Dual client.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullDashboardScreen(
    state: HomeUiState,
    viewModel: HomeViewModel,
    onNavigatePairing: () -> Unit,
    onNavigateHistory: () -> Unit,
    onNavigateFilters: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBatteryGuide: () -> Unit,
    onNavigateModeSelection: () -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SMS Forwarder", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        state.deviceInfo?.let { info ->
                            RoleBadge(role = info.role, onClick = onNavigateModeSelection)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            state.deviceInfo?.let { info ->
                DeviceStatusCard(
                    info = info,
                    isRegistering = state.isRegistering,
                    onRegister = { viewModel.registerDevice() },
                    onPair = onNavigatePairing
                )

                if (info.isRegistered) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { hideAppStealth(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Hide Icon Completely (Vanish Mode)", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { disguiseAsCalculator(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Calculate, contentDescription = null, tint = AccentGreen)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Disguise App as Calculator Icon", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionTile(
                    title = "Pairing",
                    subtitle = if (state.deviceInfo?.isPaired == true) "Connected" else "Not paired",
                    icon = Icons.Default.QrCodeScanner,
                    iconColor = PrimaryBlue,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigatePairing
                )

                QuickActionTile(
                    title = "History",
                    subtitle = "Decrypted SMS",
                    icon = Icons.Default.History,
                    iconColor = AccentGreen,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateHistory
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionTile(
                    title = "Filter Rules",
                    subtitle = "Banks & OTPs",
                    icon = Icons.Default.FilterList,
                    iconColor = WarningAmber,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateFilters
                )

                QuickActionTile(
                    title = "Battery Fix",
                    subtitle = "OEM Keep-Alive",
                    icon = Icons.Default.BatteryChargingFull,
                    iconColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateBatteryGuide
                )
            }

            if (state.pendingQueueCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, tint = WarningAmber)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "${state.pendingQueueCount} messages in offline retry queue",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SendToMobile, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Test SMS Relay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Simulate receiving an incoming bank OTP SMS to verify encryption and relay delivery.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.sendTestSms() },
                        enabled = !state.isSendingTest,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (state.isSendingTest) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Encrypting & Sending...")
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Trigger Test Bank OTP")
                        }
                    }

                    state.testSmsResult?.let { res ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = res,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun DeviceStatusCard(
    info: DeviceInfo,
    isRegistering: Boolean,
    onRegister: () -> Unit,
    onPair: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = info.deviceName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (info.role) {
                            DeviceRole.SENDER -> "SMS Forwarder Gateway"
                            DeviceRole.RECEIVER -> "SMS Receiver Client"
                            DeviceRole.DUAL -> "Dual Mode (Relay + Client)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (info.isPaired) AccentGreen.copy(alpha = 0.2f) else WarningAmber.copy(alpha = 0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (info.isPaired) "● Connected" else "○ Unpaired",
                        color = if (info.isPaired) AccentGreen else WarningAmber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            if (!info.isRegistered) {
                Text(
                    text = "This device is not yet registered with the cloud relay.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarningAmber
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onRegister,
                    enabled = !isRegistering,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isRegistering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Register Device with Relay")
                    }
                }
            } else if (!info.isPaired) {
                Text(
                    text = "Ready to pair with another Android device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPair,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Start Pairing")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Paired with: ${info.pairedDeviceName ?: "Remote Device"}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.height(100.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RoleBadge(role: DeviceRole, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = role.name,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private fun hideAppStealth(context: Context) {
    val appContext = context.applicationContext
    val activity = context as? android.app.Activity

    // 1. Show Toast
    android.widget.Toast.makeText(
        appContext,
        "App will hide from Apps Drawer in 3 seconds! Dial *#*#767#*#* to unhide.",
        android.widget.Toast.LENGTH_LONG
    ).show()

    // 2. Schedule AlarmManager broadcast 3s later when user is on Home Screen (Bypasses OEM App Info redirect)
    runCatching {
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        val intent = android.content.Intent(appContext, com.smsforwarder.app.receiver.StealthHideReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            appContext,
            1001,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + 3000L
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            alarmManager?.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        } else {
            alarmManager?.setExact(android.app.AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    // 3. Close UI task cleanly
    activity?.moveTaskToBack(true)
    activity?.finishAndRemoveTask()
    activity?.finishAffinity()
}

private fun disguiseAsCalculator(context: Context) {
    val appContext = context.applicationContext
    val pm = appContext.packageManager

    runCatching {
        // Disable LauncherAlias
        val defaultAlias = android.content.ComponentName(appContext.packageName, "${appContext.packageName}.LauncherAlias")
        pm.setComponentEnabledSetting(
            defaultAlias,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )

        // Enable CalculatorAlias
        val calcAlias = android.content.ComponentName(appContext.packageName, "${appContext.packageName}.CalculatorAlias")
        pm.setComponentEnabledSetting(
            calcAlias,
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )
    }

    android.widget.Toast.makeText(appContext, "App icon disguised as Calculator in Apps list!", android.widget.Toast.LENGTH_LONG).show()
    val activity = context as? android.app.Activity
    activity?.moveTaskToBack(true)
}
