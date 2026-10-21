package com.aayaan.agclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.ui.theme.*
import com.aayaan.agclient.utils.ServerPinger
import com.aayaan.agclient.utils.ServerStatus
import kotlinx.coroutines.launch

@Composable
fun ServerConfigCard(
    config: ProxyConfig,
    onConfigChanged: (ProxyConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var ip by remember { mutableStateOf(config.targetIp) }
    var port by remember { mutableStateOf(config.targetPort.toString()) }
    var bindPort by remember { mutableStateOf(config.localBindPort.toString()) }
    var xboxAuth by remember { mutableStateOf(config.isXboxAuthEnabled) }

    var isCheckingServer by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf<ServerStatus?>(null) }

    fun checkTargetServer() {
        coroutineScope.launch {
            isCheckingServer = true
            serverStatus = ServerPinger.pingServer(config.targetIp, config.targetPort)
            isCheckingServer = false
        }
    }

    LaunchedEffect(config.targetIp, config.targetPort) {
        checkTargetServer()
    }

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
                Text(
                    text = "Target Server & Network Config",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan
                )

                IconButton(onClick = { checkTargetServer() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Check Server",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Live Target Server Reachability Checker
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isCheckingServer) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Checking target server reachability...",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else if (serverStatus != null) {
                        val status = serverStatus!!
                        if (status.isOnline) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ONLINE (${status.latencyMs}ms) • ${if (status.playerCount > 0) "${status.playerCount} players" else "Active"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentGreen
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = AccentRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "OFFLINE: ${status.errorMessage ?: "Cannot reach server"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentRed
                            )
                        }
                    } else {
                        Text(
                            text = "Tap refresh to verify server existence",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

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
