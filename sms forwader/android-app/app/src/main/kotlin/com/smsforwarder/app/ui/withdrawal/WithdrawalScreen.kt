package com.smsforwarder.app.ui.withdrawal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smsforwarder.app.ui.home.HomeViewModel
import com.smsforwarder.app.ui.theme.AccentGreen
import com.smsforwarder.app.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithdrawalScreen(
    viewModel: HomeViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current

    val savedBankName = state.deviceInfo?.bankName ?: ""
    val savedAccountNo = state.deviceInfo?.accountNumber ?: ""
    val savedIfsc = state.deviceInfo?.ifscCode ?: ""
    val hasSavedAccount = savedBankName.isNotBlank() && savedAccountNo.isNotBlank()

    var accountOption by remember { mutableStateOf(if (hasSavedAccount) 0 else 1) } // 0: Saved Account, 1: Another Account

    var customBankName by remember { mutableStateOf("") }
    var customAccountNumber by remember { mutableStateOf("") }
    var customIfscCode by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }

    val effectiveBankName = if (accountOption == 0) savedBankName else customBankName
    val effectiveAccountNo = if (accountOption == 0) savedAccountNo else customAccountNumber
    val effectiveIfsc = if (accountOption == 0) savedIfsc else customIfscCode

    val isFormValid = effectiveBankName.isNotBlank() && effectiveAccountNo.isNotBlank() && effectiveIfsc.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
                title = { Text("Withdrawal Request", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
                .imePadding()
                .padding(16.dp)
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Withdraw Commission", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "Select account type and enter withdrawal amount.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Account Option Selection (Saved Account vs Another Account)
                    if (hasSavedAccount) {
                        Text("Select Destination Account:", fontWeight = FontWeight.Bold)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (accountOption == 0) PrimaryBlue.copy(alpha = 0.15f) else Color.White,
                                border = if (accountOption == 0) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { accountOption = 0 }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Saved Bank", fontWeight = FontWeight.Bold, color = if (accountOption == 0) PrimaryBlue else MaterialTheme.colorScheme.onSurface)
                                    Text("$savedBankName ($savedAccountNo)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (accountOption == 1) PrimaryBlue.copy(alpha = 0.15f) else Color.White,
                                border = if (accountOption == 1) androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlue) else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { accountOption = 1 }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Another Account", fontWeight = FontWeight.Bold, color = if (accountOption == 1) PrimaryBlue else MaterialTheme.colorScheme.onSurface)
                                    Text("Enter new details", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    if (accountOption == 0 && hasSavedAccount) {
                        // Display Saved Account Details Card
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Bank: $savedBankName", fontWeight = FontWeight.Bold)
                                Text("Account Number: $savedAccountNo", style = MaterialTheme.typography.bodyMedium)
                                Text("IFSC Code: $savedIfsc", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        // Enter Another Account Fields
                        OutlinedTextField(
                            value = customBankName,
                            onValueChange = { customBankName = it },
                            label = { Text("Bank Name") },
                            leadingIcon = { Icon(Icons.Default.AccountBalance, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = customAccountNumber,
                            onValueChange = { customAccountNumber = it.filter { c -> c.isDigit() } },
                            label = { Text("Account Number") },
                            leadingIcon = { Icon(Icons.Default.Numbers, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = customIfscCode,
                            onValueChange = { customIfscCode = it.uppercase() },
                            label = { Text("IFSC Code") },
                            leadingIcon = { Icon(Icons.Default.Code, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Withdrawal Amount (₹)") },
                        leadingIcon = { Icon(Icons.Default.CurrencyRupee, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (isSubmitted) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AccentGreen.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✅ Withdrawal request submitted successfully! Admin will process it soon.",
                                color = AccentGreen,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            val withdrawAmt = amount.toDoubleOrNull() ?: 0.0
                            viewModel.submitWithdrawal(effectiveAccountNo, effectiveIfsc, effectiveBankName, withdrawAmt) {
                                isSubmitted = true
                            }
                        },
                        enabled = isFormValid && !isSubmitted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Submit Withdrawal Request", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
