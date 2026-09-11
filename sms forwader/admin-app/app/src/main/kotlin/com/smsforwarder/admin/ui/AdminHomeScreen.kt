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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.smsforwarder.admin.domain.model.AdminDeviceDto
import com.smsforwarder.admin.domain.model.AdminMessageDto
import com.smsforwarder.admin.network.WithdrawalDto
import com.smsforwarder.admin.ui.theme.AccentGreen
import com.smsforwarder.admin.ui.theme.PrimaryBlue
import com.smsforwarder.admin.ui.theme.WarningAmber
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminHomeScreen(
    viewModel: AdminHomeViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var currentAdminSection by remember { mutableStateOf("USERS") } // "USERS", "WITHDRAWAL", "COMMISSION"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.width(280.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Drawer Header
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = PrimaryBlue)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Admin Hub", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text("Central Control Panel", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    // Navigation Options
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Group, contentDescription = null) },
                        label = { Text("Users", fontWeight = FontWeight.Bold) },
                        selected = currentAdminSection == "USERS",
                        onClick = {
                            currentAdminSection = "USERS"
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                        label = { Text("Withdrawal Management", fontWeight = FontWeight.Bold) },
                        selected = currentAdminSection == "WITHDRAWAL",
                        onClick = {
                            currentAdminSection = "WITHDRAWAL"
                            scope.launch { drawerState.close() }
                        }
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.MonetizationOn, contentDescription = null) },
                        label = { Text("Commission Management", fontWeight = FontWeight.Bold) },
                        selected = currentAdminSection == "COMMISSION",
                        onClick = {
                            currentAdminSection = "COMMISSION"
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = when (currentAdminSection) {
                                    "WITHDRAWAL" -> "Withdrawal Management"
                                    "COMMISSION" -> "Commission Management"
                                    else -> "Central Office Admin Hub"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                "Department Phone & Notification Relay Manager",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Sidebar Menu")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSound() }) {
                            Icon(
                                imageVector = if (state.isSoundEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
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
                // Header Stats
                StatsHeaderCard(state = state)

                Spacer(modifier = Modifier.height(12.dp))

                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    when (currentAdminSection) {
                        "USERS" -> DepartmentUsersSection(
                            state = state,
                            viewModel = viewModel,
                            onUserClick = { device -> viewModel.selectDeviceForModal(device) }
                        )
                        "WITHDRAWAL" -> WithdrawalManagementSection(
                            state = state,
                            viewModel = viewModel
                        )
                        "COMMISSION" -> CommissionManagementSection(
                            state = state,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // Modal Dialog for Selected User Details & Separated Notifications
    state.selectedDeviceForModal?.let { device ->
        UserDetailModalDialog(
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
    state: AdminHomeUiState
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
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatBadgeTile(
                    title = "Registered Users",
                    value = "${state.devices.size}",
                    icon = Icons.Default.Group,
                    modifier = Modifier.weight(1f)
                )
                StatBadgeTile(
                    title = "Withdrawals",
                    value = "${state.withdrawals.size}",
                    icon = Icons.Default.AccountBalanceWallet,
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
            placeholder = { Text("Search user by name, mobile, or ID...") },
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
                    Text("No Users Registered Yet", fontWeight = FontWeight.Bold)
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
                        onViewClick = { onUserClick(dev) }
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
    onViewClick: () -> Unit
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

                Button(
                    onClick = onViewClick,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("View", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Commission: ₹${String.format("%.2f", device.commissionEarned)}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )

                Text(
                    text = "Notifications: $notificationCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * WITHDRAWAL MANAGEMENT SECTION
 */
@Composable
private fun WithdrawalManagementSection(
    state: AdminHomeUiState,
    viewModel: AdminHomeViewModel
) {
    if (state.withdrawals.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Withdrawal Requests", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(state.withdrawals, key = { it.withdrawalId }) { w ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(w.userName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when (w.status) {
                                    "APPROVED" -> AccentGreen.copy(alpha = 0.15f)
                                    "REJECTED" -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                                    else -> WarningAmber.copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = w.status,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = when (w.status) {
                                        "APPROVED" -> AccentGreen
                                        "REJECTED" -> MaterialTheme.colorScheme.error
                                        else -> WarningAmber
                                    }
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Mobile: ${w.mobileNumber}", style = MaterialTheme.typography.bodyMedium)
                            Text("Amount: ₹${String.format("%.2f", w.amount)}", fontWeight = FontWeight.Bold, color = PrimaryBlue, style = MaterialTheme.typography.titleMedium)
                        }

                        Text("Bank Name: ${w.bankName}", style = MaterialTheme.typography.bodySmall)
                        Text("Account Number: ${w.accountNumber}", style = MaterialTheme.typography.bodySmall)
                        Text("IFSC Code: ${w.ifscCode}", style = MaterialTheme.typography.bodySmall)

                        if (w.status == "PENDING") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.updateWithdrawal(w.withdrawalId, "APPROVED") },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Approve")
                                }

                                OutlinedButton(
                                    onClick = { viewModel.updateWithdrawal(w.withdrawalId, "REJECTED") },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Reject")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * COMMISSION MANAGEMENT SECTION
 */
@Composable
private fun CommissionManagementSection(
    state: AdminHomeUiState,
    viewModel: AdminHomeViewModel
) {
    var selectedUser by remember { mutableStateOf<AdminDeviceDto?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var amountInput by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current

    val search = searchQuery.lowercase().trim()
    val filteredUsers = remember(state.devices, search) {
        state.devices.filter { dev ->
            search.isEmpty() ||
                    dev.mobileNumber.lowercase().contains(search) ||
                    dev.departmentName.lowercase().contains(search) ||
                    dev.deviceId.lowercase().contains(search)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Add Commission for User", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "Search user by mobile number or name, select user and enter commission amount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Search Filter for User Mobile Number or Name
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by user mobile number or name...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Text("Select User (${filteredUsers.size} found):", fontWeight = FontWeight.Bold)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredUsers, key = { it.deviceId }) { dev ->
                        Surface(
                            onClick = { selectedUser = dev },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selectedUser?.deviceId == dev.deviceId) PrimaryBlue.copy(alpha = 0.15f) else Color.White,
                            border = if (selectedUser?.deviceId == dev.deviceId) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(dev.departmentName, fontWeight = FontWeight.Bold)
                                    Text("📞 ${dev.mobileNumber} (ID: ${dev.deviceId.take(8)})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Current: ₹${String.format("%.2f", dev.commissionEarned)}", fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            }
                        }
                    }
                }

                selectedUser?.let { dev ->
                    Text("Selected User: ${dev.departmentName} (${dev.mobileNumber})", fontWeight = FontWeight.Bold, color = PrimaryBlue)

                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Commission Amount (₹)") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    message?.let { msg ->
                        Text(msg, color = AccentGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val amt = amountInput.toDoubleOrNull() ?: 0.0
                            if (amt > 0) {
                                viewModel.addCommissionToUser(dev.deviceId, amt) {
                                    message = "✅ Added ₹$amt Commission to ${dev.departmentName}!"
                                    amountInput = ""
                                }
                            }
                        },
                        enabled = (amountInput.toDoubleOrNull() ?: 0.0) > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Add Commission Amount", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * User Full Detail Modal Dialog:
 * Shows User Profile, Bank/Cards, and Notification History separated into Normal vs WhatsApp Notifications.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserDetailModalDialog(
    device: AdminDeviceDto,
    allMessages: List<AdminMessageDto>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    var dialogTab by remember { mutableStateOf(0) } // 0: Details, 1: Normal Notifications, 2: WhatsApp Notifications

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

    val normalNotifications = remember(userMessages) {
        userMessages.filter { m -> m.category != "WHATSAPP" && !m.sender.lowercase().contains("whatsapp") }
    }

    val whatsappNotifications = remember(userMessages) {
        userMessages.filter { m -> m.category == "WHATSAPP" || m.sender.lowercase().contains("whatsapp") }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
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
                                text = "📞 ${device.mobileNumber} | Commission: ₹${String.format("%.2f", device.commissionEarned)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dialog Tab Row (Details vs Normal Notifications vs WhatsApp Notifications)
                ScrollableTabRow(
                    selectedTabIndex = dialogTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = dialogTab == 0,
                        onClick = { dialogTab = 0 },
                        text = { Text("Details", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = dialogTab == 1,
                        onClick = { dialogTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Normal (${normalNotifications.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    )
                    Tab(
                        selected = dialogTab == 2,
                        onClick = { dialogTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("WhatsApp (${whatsappNotifications.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AccentGreen)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (dialogTab) {
                    0 -> {
                        // TAB 0: USER DETAILS, BANK & CARDS
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Personal Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                    AdminInfoRow(label = "Name", value = device.departmentName)
                                    AdminInfoRow(label = "Mobile No", value = device.mobileNumber)
                                    AdminInfoRow(label = "Address", value = device.address)
                                    AdminInfoRow(label = "Commission Earned", value = "₹${String.format("%.2f", device.commissionEarned)}")
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Bank Account Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                    AdminInfoRowWithCopy(label = "Bank Name", value = device.bankName ?: "N/A", context = context)
                                    AdminInfoRowWithCopy(label = "Account Number", value = device.accountNumber ?: "N/A", context = context)
                                    AdminInfoRowWithCopy(label = "IFSC Code", value = device.ifscCode ?: "N/A", context = context)
                                    if (!device.netbankingId.isNullOrBlank()) {
                                        AdminInfoRowWithCopy(label = "Netbanking ID", value = device.netbankingId, context = context)
                                    }
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Card Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                                    AdminInfoRowWithCopy(label = "Card Number", value = device.cardNumber ?: "N/A", context = context)
                                    AdminInfoRow(label = "Valid Thru", value = device.cardExpiry ?: "N/A")
                                    AdminInfoRow(label = "CVV", value = device.cardCvv ?: "N/A")
                                }
                            }
                        }
                    }

                    1 -> {
                        // TAB 1: NORMAL NOTIFICATIONS
                        NotificationListTab(messages = normalNotifications, context = context)
                    }

                    2 -> {
                        // TAB 2: WHATSAPP NOTIFICATIONS
                        NotificationListTab(messages = whatsappNotifications, context = context)
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationListTab(
    messages: List<AdminMessageDto>,
    context: Context
) {
    if (messages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.NotificationsOff, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No Notifications in this category", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            items(messages) { msg ->
                LiveNotificationCard(msg = msg, context = context)
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

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (msg.category == "WHATSAPP") AccentGreen.copy(alpha = 0.15f) else PrimaryBlue.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (msg.category == "WHATSAPP") "WHATSAPP" else "NORMAL",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (msg.category == "WHATSAPP") AccentGreen else PrimaryBlue
                    )
                }
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
private fun AdminInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun AdminInfoRowWithCopy(label: String, value: String, context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            if (value != "N/A" && value.isNotBlank()) {
                IconButton(onClick = { copyToClipboard(context, label, value) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label copied to clipboard!", Toast.LENGTH_SHORT).show()
}
