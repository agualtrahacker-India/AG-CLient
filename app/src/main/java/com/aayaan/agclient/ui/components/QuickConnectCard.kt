package com.aayaan.agclient.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.ui.theme.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

enum class NetworkType {
    WIFI,
    CELLULAR,
    LOOPBACK_ONLY
}

@Composable
fun QuickConnectCard(
    targetServer: String = "",
    targetPort: Int = 19132,
    localPort: Int = 19133,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var networkType by remember { mutableStateOf(NetworkType.LOOPBACK_ONLY) }
    var detectedIp by remember { mutableStateOf("127.0.0.1") }

    fun refreshNetworkState() {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(activeNetwork)

            val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

            if (isWifi) {
                networkType = NetworkType.WIFI
                // Find actual wlan interface IPv4
                var wifiIp: String? = null
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    if (intf.isUp && (intf.name.startsWith("wlan", ignoreCase = true) || intf.name.startsWith("wifi", ignoreCase = true))) {
                        for (addr in Collections.list(intf.inetAddresses)) {
                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                wifiIp = addr.hostAddress
                                break
                            }
                        }
                    }
                    if (wifiIp != null) break
                }
                detectedIp = wifiIp ?: "127.0.0.1"
            } else if (isCellular) {
                networkType = NetworkType.CELLULAR
                // On cellular data, cellular carrier CGNAT IPs (100.x.x.x) DO NOT allow self-loopback.
                // The ONLY working local address on SIM net is 127.0.0.1
                detectedIp = "127.0.0.1"
            } else {
                networkType = NetworkType.LOOPBACK_ONLY
                detectedIp = "127.0.0.1"
            }
        } catch (_: Exception) {
            networkType = NetworkType.LOOPBACK_ONLY
            detectedIp = "127.0.0.1"
        }
    }

    LaunchedEffect(Unit) {
        refreshNetworkState()
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
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
                        text = "Connect Minecraft to AG Client",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan
                    )
                }

                IconButton(onClick = { refreshNetworkState() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Network",
                        tint = NeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Upstream Target Info
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (targetServer.isNotBlank()) AccentGreen else AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (targetServer.isNotBlank()) "Target Server: $targetServer:$targetPort" else "⚠️ Target Server is empty! Enter one in Target Server & Network Config below.",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = if (targetServer.isNotBlank()) TextPrimary else AccentAmber
                    )
                }
            }

            // Connection Details Box
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when (networkType) {
                                    NetworkType.WIFI -> "Server Address (Wi-Fi LAN IP)"
                                    NetworkType.CELLULAR -> "Server Address (Mobile Data / SIM Net)"
                                    NetworkType.LOOPBACK_ONLY -> "Server Address (Local Loopback)"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = detectedIp,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = when (networkType) {
                                        NetworkType.WIFI -> Icons.Default.Wifi
                                        NetworkType.CELLULAR -> Icons.Default.SignalCellularAlt
                                        NetworkType.LOOPBACK_ONLY -> Icons.Default.PhoneAndroid
                                    },
                                    contentDescription = null,
                                    tint = when (networkType) {
                                        NetworkType.WIFI -> AccentGreen
                                        NetworkType.CELLULAR -> NeonCyan
                                        NetworkType.LOOPBACK_ONLY -> TextSecondary
                                    },
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = when (networkType) {
                                        NetworkType.WIFI -> "Active: Wi-Fi Network"
                                        NetworkType.CELLULAR -> "Active: Mobile Data (SIM Net)"
                                        NetworkType.LOOPBACK_ONLY -> "Active: Local Device Only"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when (networkType) {
                                        NetworkType.WIFI -> AccentGreen
                                        NetworkType.CELLULAR -> NeonCyan
                                        NetworkType.LOOPBACK_ONLY -> TextSecondary
                                    }
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Port",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = "$localPort",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = AccentAmber
                            )
                        }
                    }

                    // Special alert for SIM Net users
                    if (networkType == NetworkType.CELLULAR) {
                        HorizontalDivider(color = DarkCardBorder)
                        Surface(
                            color = NeonCyan.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "📱 SIM Net / Mobile Data Requirement:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan
                                )
                                Text(
                                    text = "Minecraft blocks multiplayer on SIM data by default. Open Minecraft ➡️ Settings ➡️ General (or Profile) ➡️ Turn ON 'Use Cellular Data'.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }

            // Quick Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Server Address", detectedIp)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied IP: $detectedIp", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(NeonCyan)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Address",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Copy $detectedIp", color = NeonCyan)
                }

                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Server Port", "$localPort")
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied Port: $localPort", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(AccentAmber)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy Port",
                        tint = AccentAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Copy Port", color = AccentAmber)
                }
            }

            // Launch Minecraft full width button
            Button(
                onClick = {
                    launchMinecraft(context)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Launch Minecraft",
                    tint = DarkBackground,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Launch Minecraft", color = DarkBackground, fontWeight = FontWeight.Bold)
            }

            // Connection Guide (Add Server in Minecraft)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "📌 How to Connect in Minecraft (One-Time Setup)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "1. Enter your target server IP & port in the config card below.\n" +
                                "2. Start proxy above.\n" +
                                "3. Tap 'Copy $detectedIp' and 'Launch Minecraft'.\n" +
                                "4. In Minecraft: Tap Play.\n" +
                                "5. In New 1.21+ UI: Tap Servers icon (stacked cubes 🖥️ on left sidebar) ➡️ Scroll to bottom & tap 'Add Server'.\n" +
                                "   (In Classic UI: Tap 'Servers' tab at the top ➡️ Add Server).\n" +
                                "6. Enter details:\n" +
                                "   • Server Name: AG Client\n" +
                                "   • Server Address: $detectedIp\n" +
                                "   • Port: $localPort\n" +
                                "7. Tap Save & Join!",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun launchMinecraft(context: Context) {
    val pm = context.packageManager
    val candidatePackages = listOf(
        "com.mojang.minecraftpe",
        "com.mojang.minecraftpe.beta",
        "com.mojang.minecrafttrialpe",
        "com.microsoft.minecraft"
    )

    for (pkg in candidatePackages) {
        val launchIntent = pm.getLaunchIntentForPackage(pkg)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            Toast.makeText(context, "Opening Minecraft...", Toast.LENGTH_SHORT).show()
            return
        }
    }

    Toast.makeText(context, "Minecraft is not installed on this device", Toast.LENGTH_LONG).show()
}
