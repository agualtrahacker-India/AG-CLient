package com.aayaan.agclient.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.config.ServerHistoryItem
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
    var ip by remember(config.targetIp) { mutableStateOf(config.targetIp) }
    var port by remember(config.targetPort) { mutableStateOf(config.targetPort.toString()) }
    var bindPort by remember(config.localBindPort) { mutableStateOf(config.localBindPort.toString()) }
    var xboxAuth by remember(config.isXboxAuthEnabled) { mutableStateOf(config.isXboxAuthEnabled) }

    var isCheckingServer by remember { mutableStateOf(false) }
    var serverStatus by remember { mutableStateOf<ServerStatus?>(null) }

    fun checkTargetServer() {
        val target = config.targetIp.trim()
        if (target.isBlank()) {
            serverStatus = null
            return
        }
        coroutineScope.launch {
            isCheckingServer = true
            serverStatus = ServerPinger.pingServer(target, config.targetPort)
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
                    fontWeight = FontWeight.Bold,
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
                        val message = if (ip.isBlank()) "Enter target server IP or select from history below" else "Tap refresh to verify server existence"
                        Text(
                            text = message,
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
                placeholder = { Text("e.g. play.cubecraft.net") },
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
                    label = { Text("Bind Port (Default: 19133)") },
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

            // Server History Section
            HorizontalDivider(color = DarkCardBorder, thickness = 1.dp)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Server History (1-Tap Select)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = NeonCyan
                        )
                    }
                    if (config.serverHistory.isNotEmpty()) {
                        Text(
                            text = "${config.serverHistory.size} saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }

                if (config.serverHistory.isEmpty()) {
                    Surface(
                        color = DarkCard,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No saved servers yet. Servers you join will be remembered here for 1-tap switching.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        config.serverHistory.forEach { item ->
                            val isCurrentTarget = item.ip.equals(config.targetIp.trim(), ignoreCase = true) && item.port == config.targetPort
                            Surface(
                                color = if (isCurrentTarget) DarkCard.copy(alpha = 0.9f) else DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = if (isCurrentTarget) BorderStroke(1.dp, NeonCyan) else BorderStroke(1.dp, DarkCardBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        ip = item.ip
                                        port = item.port.toString()
                                        config.targetIp = item.ip
                                        config.targetPort = item.port
                                        onConfigChanged(config)
                                        checkTargetServer()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = if (item.name.isNotBlank() && item.name != item.ip) item.name else item.ip,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isCurrentTarget) NeonCyan else TextPrimary
                                            )
                                            if (isCurrentTarget) {
                                                Surface(
                                                    color = NeonCyan.copy(alpha = 0.2f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "ACTIVE",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = NeonCyan,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${item.ip}:${item.port}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            config.serverHistory.removeAll { it.ip.equals(item.ip, ignoreCase = true) && it.port == item.port }
                                            onConfigChanged(config)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove from history",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
