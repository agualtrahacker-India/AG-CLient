package com.aayaan.agclient.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.ui.theme.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

@Composable
fun QuickConnectCard(
    vpnActive: Boolean,
    onToggleVpn: (Boolean) -> Unit,
    targetServer: String = "geo.hivebedrock.network",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var localIp by remember { mutableStateOf("127.0.0.1") }

    LaunchedEffect(Unit) {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty()) {
                            localIp = host
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, NeonCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = NeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "🎮 How to Join in Minecraft (No IP Typing!)",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan
                )
            }

            // Option 1: 1-Tap Official Server Redirect
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Option 1: 1-Tap Official Server Redirect (VPN Mode)",
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary
                            )
                            Text(
                                text = "Routes to Selected Server: $targetServer",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeonCyan
                            )
                            Text(
                                text = "Zero IP typing. Tap any featured server in Minecraft to join!",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Switch(
                            checked = vpnActive,
                            onCheckedChange = { onToggleVpn(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = NeonCyan
                            )
                        )
                    }

                    if (vpnActive) {
                        Text(
                            text = "✓ Active! Open Minecraft -> Servers -> Tap any server (The Hive, CubeCraft, etc.) to connect straight into $targetServer!",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentGreen
                        )
                    }
                }
            }

            // Option 2: Friends Tab (LAN Games)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                        Text(
                            text = "Option 2: Friends Tab (LAN Game Discovery)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "• Open Minecraft -> Tap 'Friends' tab -> Scroll down to 'LAN Games' -> Tap 'AG Client Beta'.\n• Automatically connects you directly to $targetServer!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }

            // Option 3: Manual Servers Tab
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = "Option 3 (Saved Server):", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(text = "$localIp:19132", style = MaterialTheme.typography.bodyMedium, color = AccentAmber)
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Server IP", localIp)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied IP: $localIp", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy IP", tint = NeonCyan)
                    }
                }
            }
        }
    }
}
