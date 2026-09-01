package com.smsforwarder.app.ui.mode

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsforwarder.app.domain.model.DeviceRole
import com.smsforwarder.app.ui.theme.AccentGreen
import com.smsforwarder.app.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectionScreen(
    currentRole: DeviceRole,
    onRoleSelected: (DeviceRole) -> Unit,
    onNavigateHome: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Choose Device Role", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Select how you want this phone to function in your SMS relay network:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // SENDER CARD
            RoleCard(
                title = "Forwarder / Sender (Gateway)",
                subtitle = "This phone has the SIM card. It captures bank SMS & OTPs, encrypts them, and forwards them to your other device.",
                icon = Icons.Default.Send,
                iconColor = PrimaryBlue,
                isSelected = currentRole == DeviceRole.SENDER,
                onClick = {
                    onRoleSelected(DeviceRole.SENDER)
                    onNavigateHome()
                }
            )

            // RECEIVER CARD
            RoleCard(
                title = "Receiver / Destination",
                subtitle = "This is your primary device. It receives encrypted SMS via push notifications, decrypts them securely, and gives you 1-tap OTP copy buttons.",
                icon = Icons.Default.CallReceived,
                iconColor = AccentGreen,
                isSelected = currentRole == DeviceRole.RECEIVER,
                onClick = {
                    onRoleSelected(DeviceRole.RECEIVER)
                    onNavigateHome()
                }
            )

            // DUAL MODE CARD
            RoleCard(
                title = "Dual Mode (Bidirectional)",
                subtitle = "Both forward incoming SIM SMS and receive forwarded SMS from paired devices. Ideal for cross-forwarding between two active phones.",
                icon = Icons.Default.SyncAlt,
                iconColor = MaterialTheme.colorScheme.secondary,
                isSelected = currentRole == DeviceRole.DUAL,
                onClick = {
                    onRoleSelected(DeviceRole.DUAL)
                    onNavigateHome()
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onNavigateHome,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Continue to Dashboard", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(36.dp)
                    .padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                RadioButton(
                    selected = true,
                    onClick = onClick
                )
            }
        }
    }
}
