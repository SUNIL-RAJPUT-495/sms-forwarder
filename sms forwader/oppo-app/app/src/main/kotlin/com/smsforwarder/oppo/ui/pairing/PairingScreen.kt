package com.smsforwarder.oppo.ui.pairing

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * OPPO Pairing Screen — Phase 5 full implementation.
 *
 * Supports two pairing entry paths:
 *   1. **ADB intent** (primary — OPPO screen is broken):
 *      `adb shell am start -n com.smsforwarder.oppo/.MainActivity
 *       --es pairing_token "A3F-7K2"`
 *      This triggers the ViewModel automatically — no UI interaction needed.
 *
 *   2. **Manual entry** (if screen is partially visible):
 *      User types the 7-char code from Samsung's Pairing screen.
 *
 * The ADB command card auto-updates as the user types the token
 * so they can copy-paste it directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onNavigateBack: () -> Unit,
    viewModel: PairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tokenInput by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair with Samsung", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── Paired status banner ──────────────────────────────
            AnimatedVisibility(visible = uiState.isPaired) {
                PairedBanner(deviceName = uiState.destinationName)
            }

            // ─── How-to card ───────────────────────────────────────
            if (!uiState.isPaired) {
                StepCard(
                    step = "1",
                    title = "Get code from Samsung",
                    body = "Open the SMS Forwarder app on your Samsung phone → Pair with OPPO → note the 7-char code."
                )
                StepCard(
                    step = "2",
                    title = "Enter code below",
                    body = "Type the code (e.g. A3F-7K2) in the field below, or use the ADB command — the command updates as you type."
                )
            }

            // ─── Token input ───────────────────────────────────────
            if (!uiState.isPaired) {
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { new ->
                        // Allow letters, digits and dash only. Max 7 chars.
                        val filtered = new.filter { it.isLetterOrDigit() || it == '-' }
                            .uppercase()
                            .take(7)
                        tokenInput = filtered
                        viewModel.clearResult()
                    },
                    label = { Text("Pairing code") },
                    placeholder = { Text("A3F-7K2", fontFamily = FontFamily.Monospace) },
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        letterSpacing = 4.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = ImeAction.Done.let { KeyboardCapitalization.Characters },
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            viewModel.submitToken(tokenInput)
                        }
                    ),
                    trailingIcon = {
                        if (tokenInput.isNotEmpty()) {
                            IconButton(onClick = { tokenInput = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Submit button
                Button(
                    onClick = {
                        keyboard?.hide()
                        viewModel.submitToken(tokenInput)
                    },
                    enabled = tokenInput.length >= 3 && !uiState.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Pairing…")
                    } else {
                        Icon(Icons.Outlined.Link, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Complete Pairing")
                    }
                }

                // ─── Result feedback ───────────────────────────────
                AnimatedVisibility(visible = uiState.pairingResult != null) {
                    uiState.pairingResult?.let { outcome ->
                        PairingResultChip(outcome, onDismiss = viewModel::clearResult)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ─── ADB alternative ───────────────────────────────
                Text(
                    "Alternative: ADB command (for broken screen)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )

                val displayToken = tokenInput.ifBlank { "A3F-7K2" }
                AdbCommandCard(token = displayToken)

                Text(
                    "Run the command above in a terminal on your computer while OPPO is connected via USB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PairedBanner(deviceName: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.CheckCircle, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Column {
                Text(
                    "Paired with Samsung",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (deviceName.isNotBlank()) {
                    Text(
                        deviceName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: String, title: String, body: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    step,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdbCommandCard(token: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "adb shell am start \\\n" +
                   "  -n com.smsforwarder.oppo/.MainActivity \\\n" +
                   "  --es pairing_token \"$token\"",
            modifier = Modifier.padding(14.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PairingResultChip(outcome: PairingOutcome, onDismiss: () -> Unit) {
    val (icon, text, color) = when (outcome) {
        is PairingOutcome.Success      -> Triple(Icons.Filled.CheckCircle,
            "✓ Paired with ${outcome.deviceName}",
            MaterialTheme.colorScheme.primary)
        is PairingOutcome.InvalidToken -> Triple(Icons.Outlined.ErrorOutline,
            "Invalid or expired code — check the code on Samsung and try again",
            MaterialTheme.colorScheme.error)
        is PairingOutcome.TokenUsed    -> Triple(Icons.Outlined.ErrorOutline,
            "Code already used — generate a new code on Samsung",
            MaterialTheme.colorScheme.error)
        is PairingOutcome.NetworkError -> Triple(Icons.Outlined.WifiOff,
            "Network error — check connection and retry",
            MaterialTheme.colorScheme.tertiary)
        is PairingOutcome.Error        -> Triple(Icons.Outlined.ErrorOutline,
            "Error: ${outcome.msg}",
            MaterialTheme.colorScheme.error)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = color,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, null, tint = color, modifier = Modifier.size(14.dp))
            }
        }
    }
}
