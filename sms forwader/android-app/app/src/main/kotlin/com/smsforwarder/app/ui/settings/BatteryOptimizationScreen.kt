package com.smsforwarder.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsforwarder.app.ui.theme.AccentGreen
import com.smsforwarder.app.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryOptimizationScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OEM Keep-Alive Guide", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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

            // Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Many phone manufacturers (Samsung, Xiaomi, Oppo, etc.) aggressively kill background apps when screen turns off. Follow the guide for your phone brand below to ensure 100% reliable SMS relay.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Quick Whitelist Button
            Button(
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Android Battery Whitelist Dialog", fontWeight = FontWeight.Bold)
            }

            Text(
                text = "Manufacturer-Specific Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // 1. SAMSUNG (OneUI)
            OemGuideCard(
                brand = "Samsung (Galaxy / OneUI)",
                steps = listOf(
                    "Settings → Battery → Background usage limits → Never sleeping apps → Add (+) SMS Forwarder.",
                    "Settings → Apps → SMS Forwarder → Battery → Set to 'Unrestricted'.",
                    "Settings → Device care → Auto optimization → Ensure 'Auto restart' does not kill active tasks."
                )
            )

            // 2. XIAOMI / REDMI / POCO (MIUI / HyperOS)
            OemGuideCard(
                brand = "Xiaomi / Redmi / POCO (MIUI / HyperOS)",
                steps = listOf(
                    "Settings → Apps → Manage apps → SMS Forwarder → Autostart: Set to ON.",
                    "Settings → Apps → Manage apps → SMS Forwarder → Battery saver: Choose 'No restrictions'.",
                    "Open Recent Apps screen → Long press SMS Forwarder card → Tap Lock (Padlock) icon."
                )
            )

            // 3. OPPO & REALME (ColorOS / RealmeUI)
            OemGuideCard(
                brand = "OPPO & Realme (ColorOS / RealmeUI)",
                steps = listOf(
                    "Settings → App management → SMS Forwarder → Battery usage → Allow background activity: ON.",
                    "Settings → App management → SMS Forwarder → Allow auto-launch: ON.",
                    "Settings → Battery → More battery settings → App battery management → SMS Forwarder → Don't optimize."
                )
            )

            // 4. ONEPLUS (OxygenOS)
            OemGuideCard(
                brand = "OnePlus (OxygenOS)",
                steps = listOf(
                    "Settings → Battery → More settings → App battery management → SMS Forwarder → Allow background activity.",
                    "Settings → Apps → Auto-launch → Enable for SMS Forwarder.",
                    "Settings → Battery → Battery optimization → SMS Forwarder → 'Don't optimize'."
                )
            )

            // 5. GOOGLE PIXEL & MOTOROLA (Stock Android)
            OemGuideCard(
                brand = "Google Pixel / Motorola / Sony (Stock)",
                steps = listOf(
                    "Settings → Apps → SMS Forwarder → App battery usage → Set to 'Unrestricted'.",
                    "Settings → Network & internet → Data Saver → Unrestricted data access → Turn ON for SMS Forwarder."
                )
            )

            // 6. VIVO & IQOO (FuntouchOS / OriginOS)
            OemGuideCard(
                brand = "Vivo & iQOO (FuntouchOS / OriginOS)",
                steps = listOf(
                    "Settings → Battery → High background power consumption → Turn ON for SMS Forwarder.",
                    "Settings → Apps and permissions → Autostart → Enable for SMS Forwarder.",
                    "Recent Apps screen → Pull down on SMS Forwarder to lock."
                )
            )

            // 7. HUAWEI & HONOR (EMUI / MagicOS)
            OemGuideCard(
                brand = "Huawei & Honor (EMUI / HarmonyOS)",
                steps = listOf(
                    "Settings → Battery → App launch → Find SMS Forwarder → Set to 'Manage manually'.",
                    "Enable 'Auto-launch', 'Secondary launch', and 'Run in background'."
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OemGuideCard(
    brand: String,
    steps: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = brand,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(12.dp))
                steps.forEachIndexed { idx, step ->
                    Row(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = "${idx + 1}. ",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = step,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
