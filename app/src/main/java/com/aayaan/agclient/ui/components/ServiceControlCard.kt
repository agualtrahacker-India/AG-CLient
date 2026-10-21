package com.aayaan.agclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.proxy.ServiceState
import com.aayaan.agclient.ui.theme.*

@Composable
fun ServiceControlCard(
    serviceState: ServiceState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Proxy Foreground Service", style = MaterialTheme.typography.titleMedium)
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
                        color = if (serviceState == ServiceState.RUNNING) TextPrimary else DarkBackground
                    )
                }
            }

            if (serviceState == ServiceState.RUNNING) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✓ Listening on 127.0.0.1:19132\n✓ LAN Pong active: Appears in Minecraft under 'Friends' -> 'LAN Worlds'",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentGreen,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
