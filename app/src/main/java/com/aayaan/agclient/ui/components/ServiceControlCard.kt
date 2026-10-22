package com.aayaan.agclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.proxy.ServiceState
import com.aayaan.agclient.ui.theme.*

@Composable
fun ServiceControlCard(
    serviceState: ServiceState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    isSandboxRunning: Boolean = false,
    onToggleSandbox: () -> Unit = {},
    localPort: Int = 19133,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Bedrock Proxy Foreground Service
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Proxy Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    when (serviceState) {
                                        ServiceState.RUNNING -> AccentGreen
                                        ServiceState.STARTING -> AccentAmber
                                        ServiceState.ERROR -> AccentRed
                                        ServiceState.STOPPED -> TextSecondary
                                    },
                                    CircleShape
                                )
                        )
                        Text(
                            text = serviceState.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (serviceState) {
                                ServiceState.RUNNING -> AccentGreen
                                ServiceState.STARTING -> AccentAmber
                                ServiceState.ERROR -> AccentRed
                                ServiceState.STOPPED -> TextSecondary
                            }
                        )
                    }
                }

                Button(
                    onClick = {
                        if (serviceState == ServiceState.RUNNING) {
                            onStopService()
                        } else {
                            onStartService()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serviceState == ServiceState.RUNNING) AccentRed else NeonCyan
                    )
                ) {
                    Text(
                        text = if (serviceState == ServiceState.RUNNING) "STOP PROXY" else "START PROXY",
                        color = if (serviceState == ServiceState.RUNNING) TextPrimary else DarkBackground,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (serviceState == ServiceState.RUNNING) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Local Proxy active on port $localPort\n✓ In Minecraft: Go to Servers ➡️ Add Server (Address: 127.0.0.1 or 10.0.0.1 with Sandbox)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentGreen,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            HorizontalDivider(color = DarkCardBorder)

            // 2. Virtual Sandbox Mode (Auto-Router)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            tint = if (isSandboxRunning) ElectricPurple else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Virtual Sandbox Router",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSandboxRunning) ElectricPurple else TextPrimary
                        )
                    }
                    Text(
                        text = if (isSandboxRunning) "Active (Sandbox IP: 10.0.0.1)" else "Offline (Tap Start to Run Sandbox)",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSandboxRunning) AccentGreen else TextSecondary
                    )
                }

                Button(
                    onClick = onToggleSandbox,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSandboxRunning) AccentRed else ElectricPurple
                    )
                ) {
                    Text(
                        text = if (isSandboxRunning) "STOP SANDBOX" else "START SANDBOX",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sandbox info status banner
            Surface(
                color = if (isSandboxRunning) ElectricPurple.copy(alpha = 0.12f) else DarkCard,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSandboxRunning) {
                        Text(
                            text = "✓ Virtual Sandbox Active (IP: 10.0.0.1)\n" +
                                   "✓ Minecraft Server Address: 10.0.0.1 | Port: $localPort\n" +
                                   "✓ Normal Internet & Xbox Live Login stay 100% active!\n" +
                                   "✓ Completely bypasses cellular carrier blocks & connection timeouts",
                            style = MaterialTheme.typography.bodySmall,
                            color = AccentGreen
                        )
                    } else {
                        Text(
                            text = "💡 Virtual Sandbox creates a 100% local virtual adapter (10.0.0.1) on your phone. Allows Minecraft to connect without cellular carrier blocks while keeping Xbox Live & normal internet 100% working.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
