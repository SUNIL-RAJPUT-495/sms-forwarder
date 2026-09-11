package com.smsforwarder.app.ui.withdrawal

import androidx.compose.foundation.background
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

    var bankName by remember { mutableStateOf(state.deviceInfo?.bankName ?: "") }
    var accountNumber by remember { mutableStateOf(state.deviceInfo?.accountNumber ?: "") }
    var ifscCode by remember { mutableStateOf(state.deviceInfo?.ifscCode ?: "") }
    var amount by remember { mutableStateOf("") }
    var isSubmitted by remember { mutableStateOf(false) }

    val isFormValid = bankName.isNotBlank() && accountNumber.isNotBlank() && ifscCode.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0

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
                        text = "Enter your bank details and withdrawal amount.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = bankName,
                        onValueChange = { bankName = it },
                        label = { Text("Bank Name") },
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
                            viewModel.submitWithdrawal(accountNumber, ifscCode, bankName, withdrawAmt) {
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
