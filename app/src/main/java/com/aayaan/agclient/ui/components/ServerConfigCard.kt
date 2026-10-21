package com.aayaan.agclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.ui.theme.*

@Composable
fun ServerConfigCard(
    config: ProxyConfig,
    onConfigChanged: (ProxyConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    var ip by remember { mutableStateOf(config.targetIp) }
    var port by remember { mutableStateOf(config.targetPort.toString()) }
    var bindPort by remember { mutableStateOf(config.localBindPort.toString()) }
    var xboxAuth by remember { mutableStateOf(config.isXboxAuthEnabled) }

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
            Text(
                text = "Target Server & Network Config",
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan
            )

            OutlinedTextField(
                value = ip,
                onValueChange = {
                    ip = it
                    config.targetIp = it
                    onConfigChanged(config)
                },
                label = { Text("Target Server IP / Hostname") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NeonCyan,
                    unfocusedBorderColor = DarkCardBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = port,
                    onValueChange = {
                        port = it
                        it.toIntOrNull()?.let { p ->
                            config.targetPort = p
                            onConfigChanged(config)
                        }
                    },
                    label = { Text("Target Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                OutlinedTextField(
                    value = bindPort,
                    onValueChange = {
                        bindPort = it
                        it.toIntOrNull()?.let { bp ->
                            config.localBindPort = bp
                            onConfigChanged(config)
                        }
                    },
                    label = { Text("Local Bind Port") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DarkCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Online Mode (Xbox Auth)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (xboxAuth) "Sign outbound chain with Xbox Live token" else "Self-signed offline player chain",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Switch(
                    checked = xboxAuth,
                    onCheckedChange = {
                        xboxAuth = it
                        config.isXboxAuthEnabled = it
                        onConfigChanged(config)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = NeonCyan,
                        checkedTrackColor = DarkCard
                    )
                )
            }
        }
    }
}
