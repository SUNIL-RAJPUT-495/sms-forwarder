package com.smsforwarder.samsung.ui.pairing

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

/**
 * Samsung Pairing Screen — Phase 5 full implementation.
 *
 * Shows a real pairing token fetched from the backend.
 * The user reads this token and enters it on OPPO via:
 *   adb shell am start -n com.smsforwarder.oppo/.MainActivity
 *     --es pairing_token "TOKEN"
 *
 * Features:
 *   - Auto-requests token on first screen load
 *   - Live countdown to token expiry
 *   - ADB command card with the real token pre-filled
 *   - "Generate New Code" button to refresh
 *   - Paired state banner (shown after OPPO completes pairing)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(
    onNavigateBack: () -> Unit,
    viewModel: SamsungPairingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Auto-request token on first load if not already paired
    LaunchedEffect(Unit) {
        if (!uiState.isPaired && uiState.pairingToken.isEmpty()) {
            viewModel.requestToken()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair with OPPO", fontWeight = FontWeight.Bold) },
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── Paired banner ─────────────────────────────────────
            AnimatedVisibility(visible = uiState.isPaired) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
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
                        Text(
                            "Paired with OPPO device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (!uiState.isPaired) {
                Icon(
                    Icons.Outlined.QrCode2, null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(
                    "Pairing Code",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Show this code to the OPPO phone or enter it via ADB command below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // ─── Token card ────────────────────────────────────
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isLoadingToken) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else if (uiState.pairingToken.isNotBlank()) {
                            Text(
                                text = uiState.pairingToken,
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 6.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Text(
                                "─ ─ ─",
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Light
                                ),
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                // Expiry countdown
                if (uiState.tokenExpiresAt > 0 && !uiState.isLoadingToken) {
                    TokenExpiryCountdown(expiresAt = uiState.tokenExpiresAt)
                } else {
                    Text(
                        "Valid for 10 minutes · Single use",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // ─── Error feedback ────────────────────────────────
                uiState.error?.let { err ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f))
                            IconButton(onClick = viewModel::clearError, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.Close, null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }

                // ─── Generate button ───────────────────────────────
                Button(
                    onClick = viewModel::requestToken,
                    enabled = !uiState.isLoadingToken,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (uiState.pairingToken.isEmpty()) "Generate Code" else "Generate New Code")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // ─── ADB command ───────────────────────────────────
                Text(
                    "Enter via ADB (OPPO broken screen)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )

                val displayToken = uiState.pairingToken.ifBlank { "<token>" }
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "adb shell am start \\\n" +
                               "  -n com.smsforwarder.oppo/.MainActivity \\\n" +
                               "  --es pairing_token \"$displayToken\"",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    "Connect OPPO via USB and run the command above in a terminal. " +
                    "Pairing completes automatically when OPPO submits the code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Cancel") }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TokenExpiryCountdown(expiresAt: Long) {
    var remainingMs by remember { mutableLongStateOf(expiresAt - System.currentTimeMillis()) }

    LaunchedEffect(expiresAt) {
        while (remainingMs > 0) {
            delay(1000)
            remainingMs = expiresAt - System.currentTimeMillis()
        }
    }

    val expired = remainingMs <= 0
    val minutes  = TimeUnit.MILLISECONDS.toMinutes(remainingMs.coerceAtLeast(0))
    val seconds  = TimeUnit.MILLISECONDS.toSeconds(remainingMs.coerceAtLeast(0)) % 60

    val color = if (expired || minutes < 1)
        MaterialTheme.colorScheme.error
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = if (expired) "⚠ Token expired — generate a new code"
               else "Expires in %d:%02d · Single use".format(minutes, seconds),
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

/**
 * Samsung Pairing screen.
 *
 * Samsung generates a pairing token and displays it as both:
 * 1. A QR code (for scanning)
 * 2. A 6-character alphanumeric code (for manual entry via ADB on OPPO)
 *
 * Since OPPO's screen is broken, the user enters the code via:
 *   adb shell am start -n com.smsforwarder.oppo/.MainActivity --es pairing_token "ABC-XYZ"
 *
 * Full implementation in Phase 5 (backend pairing API integration).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairingScreen(onNavigateBack: () -> Unit) {
    // Phase 5: replace with real token from backend
    val mockToken = "A3F-7K2"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pair with OPPO", fontWeight = FontWeight.Bold) },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Icon(
                Icons.Outlined.QrCode,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                "Pairing Code",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Share this code with the OPPO device. Because OPPO's screen is broken, " +
                "enter it via ADB command shown below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            // Pairing token display
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = mockToken,
                    modifier = Modifier.padding(horizontal = 40.dp, vertical = 20.dp),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 8.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Text(
                "Valid for 10 minutes · Single use",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            Text(
                "Enter via ADB (OPPO broken screen)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            // ADB command display
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "adb shell am start \\\n" +
                           "  -n com.smsforwarder.oppo/.MainActivity \\\n" +
                           "  --es pairing_token \"$mockToken\"",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = { /* Phase 5: refresh token from backend */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Generate New Code")
            }

            OutlinedButton(
                onClick = onNavigateBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancel")
            }
        }
    }
}
