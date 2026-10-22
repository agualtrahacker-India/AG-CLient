package com.aayaan.agclient.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.auth.XboxAuthState
import com.aayaan.agclient.ui.theme.*

@Composable
fun XboxAuthCard(
    authState: XboxAuthState? = null,
    currentIp: String = "10.0.0.1",
    localPort: Int = 19133,
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = ElectricPurple,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Minecraft Profile & Identity",
                    style = MaterialTheme.typography.titleMedium,
                    color = ElectricPurple
                )
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Native Minecraft Passthrough Active",
                            style = MaterialTheme.typography.titleSmall,
                            color = AccentGreen
                        )
                    }

                    Text(
                        text = "• Uses your actual in-game Minecraft profile, Gamertag, and skin automatically.\n• No web browser login or device code required.\n• Simply tap 'START SANDBOX' or 'START PROXY', open Minecraft, and connect via Servers ➡️ Add Server ($currentIp:$localPort)!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
