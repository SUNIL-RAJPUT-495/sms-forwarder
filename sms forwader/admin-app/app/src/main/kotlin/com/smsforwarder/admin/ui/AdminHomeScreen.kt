package com.smsforwarder.admin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smsforwarder.admin.domain.model.AdminDeviceDto
import com.smsforwarder.admin.domain.model.AdminMessageDto
import com.smsforwarder.admin.ui.theme.AccentGreen
import com.smsforwarder.admin.ui.theme.PrimaryBlue
import com.smsforwarder.admin.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    viewModel: AdminHomeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(0) } // 0: Department Users, 1: Live Stream

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Central Office Admin Hub", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Department Phone & Notification Relay Manager",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSound() }) {
                        Icon(
                            imageVector = if (state.isSoundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = "Toggle Sound",
                            tint = if (state.isSoundEnabled) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.fetchData(showLoading = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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
        ) {
            // Live Status & Stat Cards Header
            StatsHeaderCard(
                state = state,
                onAddDemoUser = { viewModel.simulateDemoDevice() },
                onTestNotification = { viewModel.simulateTestSms() }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation Tabs (Department Users vs Live Stream)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.clip(RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Users & Phones (${state.devices.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, modifier = Modifier.size(18.dp), tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Live Feed (${state.messages.size})", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (selectedTab == 0) {
                // DEPARTMENT USERS DIRECTORY TAB
                DepartmentUsersSection(
                    state = state,
                    viewModel = viewModel,
                    onUserClick = { device -> viewModel.selectDeviceForModal(device) }
                )
            } else {
                // LIVE NOTIFICATION STREAM TAB
                LiveNotificationsSection(
                    state = state,
                    viewModel = viewModel,
                    context = context
                )
            }
        }
    }

    // Modal Dialog for Selected User's Full Notification History
    state.selectedDeviceForModal?.let { device ->
        UserHistoryModalDialog(
            device = device,
            allMessages = state.messages,
            searchQuery = state.searchMessageQuery,
            onSearchQueryChange = { viewModel.setSearchMessageQuery(it) },
            onDismiss = { viewModel.selectDeviceForModal(null) },
            context = context
        )
    }
}

@Composable
private fun StatsHeaderCard(
    state: AdminHomeUiState,
    onAddDemoUser: () -> Unit,
    onTestNotification: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (state.errorMessage == null) AccentGreen else WarningAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (state.errorMessage == null) "LIVE RELAY CONNECTED" else "OFFLINE PENDING",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (state.errorMessage == null) AccentGreen else WarningAmber
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onAddDemoUser,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("+ Demo User", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onTestNotification,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("🧪 Test SMS", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBadgeTile(
                    title = "Registered Phones",
                    value = "${state.devices.size}",
                    icon = Icons.Default.Smartphone,
                    modifier = Modifier.weight(1f)
                )
                StatBadgeTile(
                    title = "Total Notifications",
                    value = "${state.messages.size}",
                    icon = Icons.Default.MarkChatUnread,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatBadgeTile(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DepartmentUsersSection(
    state: AdminHomeUiState,
    viewModel: AdminHomeViewModel,
    onUserClick: (AdminDeviceDto) -> Unit
) {
    val search = state.searchDeviceQuery.lowercase().trim()
    val filteredDevices = remember(state.devices, search) {
        state.devices.filter { dev ->
            search.isEmpty() ||
                    dev.departmentName.lowercase().contains(search) ||
                    dev.mobileNumber.lowercase().contains(search) ||
                    dev.address.lowercase().contains(search)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchDeviceQuery,
            onValueChange = { viewModel.setSearchDeviceQuery(it) },
            placeholder = { Text("Search user, mobile, or department...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhonelinkOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No Department Phones Registered Yet", fontWeight = FontWeight.Bold)
                    Text("Tap '+ Demo User' or register from department phone app", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredDevices, key = { it.deviceId }) { dev ->
                    val userNotiCount = state.messages.count { m ->
                        m.deviceId == dev.deviceId ||
                                m.mobileNumber == dev.mobileNumber ||
                                m.departmentName.equals(dev.departmentName, ignoreCase = true)
                    }

                    DepartmentUserCard(
                        device = dev,
                        notificationCount = userNotiCount,
                        onClick = { onUserClick(dev) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DepartmentUserCard(
    device: AdminDeviceDto,
    notificationCount: Int,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = PrimaryBlue.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue)
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = device.departmentName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "📞 ${device.mobileNumber}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (device.isOnline) AccentGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = if (device.isOnline) "ONLINE" else "OFFLINE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (device.isOnline) AccentGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "📍 Address: ${device.address}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PrimaryBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "📩 $notificationCount Notifications",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "View All Notifications →",
                    style = MaterialTheme.typography.labelMedium,
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun LiveNotificationsSection(
    state: AdminHomeUiState,
    viewModel: AdminHomeViewModel,
    context: Context
) {
    val search = state.searchMessageQuery.lowercase().trim()
    val filteredMessages = remember(state.messages, search) {
        state.messages.filter { msg ->
            search.isEmpty() ||
                    msg.body.lowercase().contains(search) ||
                    msg.sender.lowercase().contains(search) ||
                    msg.departmentName.lowercase().contains(search) ||
                    (msg.otp != null && msg.otp.contains(search))
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.searchMessageQuery,
            onValueChange = { viewModel.setSearchMessageQuery(it) },
            placeholder = { Text("Search notifications, OTP, bank alerts...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (filteredMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Waiting for Live Notifications...", fontWeight = FontWeight.Bold)
                    Text("Incoming SMS from department phones will stream here live.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredMessages, key = { it.messageId.ifBlank { it.id.ifBlank { it.body } } }) { msg ->
                    LiveNotificationCard(msg = msg, context = context)
                }
            }
        }
    }
}

@Composable
private fun LiveNotificationCard(
    msg: AdminMessageDto,
    context: Context
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = msg.departmentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )

                Text(
                    text = msg.receivedAt?.take(16)?.replace("T", " ") ?: "Just now",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SENDER: ${msg.sender}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!msg.otp.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = AccentGreen.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("DETECTED OTP", style = MaterialTheme.typography.labelSmall, color = AccentGreen)
                            Text(msg.otp, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AccentGreen)
                        }

                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("OTP", msg.otp))
                                Toast.makeText(context, "OTP ${msg.otp} copied!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("COPY OTP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = msg.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("SMS", msg.body))
                        Toast.makeText(context, "Full message text copied!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("Copy Message", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun UserHistoryModalDialog(
    device: AdminDeviceDto,
    allMessages: List<AdminMessageDto>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    val search = searchQuery.lowercase().trim()
    val userMessages = remember(allMessages, device, search) {
        allMessages.filter { m ->
            val isUser = m.deviceId == device.deviceId ||
                    m.mobileNumber == device.mobileNumber ||
                    m.departmentName.equals(device.departmentName, ignoreCase = true)
            val matchesSearch = search.isEmpty() ||
                    m.body.lowercase().contains(search) ||
                    m.sender.lowercase().contains(search) ||
                    (m.otp != null && m.otp.contains(search))
            isUser && matchesSearch
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📜 ${device.departmentName}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Mobile: ${device.mobileNumber} | ${device.address}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search in this user's notifications...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${userMessages.size} Total Notifications",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (userMessages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.MarkAsUnread, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No Notifications Found for This User", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        items(userMessages) { msg ->
                            LiveNotificationCard(msg = msg, context = context)
                        }
                    }
                }
            }
        }
    }
}
