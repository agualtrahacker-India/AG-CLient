package com.aayaan.agclient.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.ui.theme.*
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections

@Composable
fun QuickConnectCard(
    targetServer: String = "",
    targetPort: Int = 19132,
    localPort: Int = 19133,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var wifiIp by remember { mutableStateOf<String?>(null) }
    var targetStatus by remember { mutableStateOf<com.aayaan.agclient.utils.ServerStatus?>(null) }
    var isCheckingTarget by remember { mutableStateOf(false) }

    LaunchedEffect(targetServer, targetPort) {
        if (targetServer.isNotBlank()) {
            isCheckingTarget = true
            targetStatus = com.aayaan.agclient.utils.ServerPinger.pingServer(targetServer, targetPort)
            isCheckingTarget = false
        } else {
            targetStatus = null
        }
    }

    LaunchedEffect(Unit) {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val addrs = Collections.list(intf.inetAddresses)
                for (addr in addrs) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty() && !host.startsWith("10.8.")) {
                            wifiIp = host
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
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

            // Upstream Target Info
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
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (targetServer.isNotBlank()) AccentGreen else AccentAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (targetServer.isNotBlank()) "Forwarding to: $targetServer:$targetPort" else "No target server selected (Set in config below)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (targetServer.isNotBlank()) TextPrimary else AccentAmber
                    )
                }
            }

            val loopbackIp = "127.0.0.1"

            // IP & Port Details Box (Highlighting 127.0.0.1 permanent static loopback)
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkBackground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkCardBorder, RoundedCornerShape(10.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Server Address (Same Phone)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Text(
                                    text = "🔒 Never Changes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = loopbackIp,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Text(
                                text = "Works permanently on Wi-Fi, 4G/5G data & offline",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
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

                    if (wifiIp != null) {
                        Divider(color = DarkCardBorder)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LAN Wi-Fi IP (for other phones):",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                            Text(
                                text = wifiIp ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            // Quick Action Buttons (Copy IP & Copy Port)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Server IP", loopbackIp)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied IP: $loopbackIp", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkCard),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(NeonCyan)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy IP",
                        tint = NeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Copy 127.0.0.1", color = NeonCyan)
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

            // Step-by-Step Instructions for New 1.21+ Ore UI & Classic UI
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Method 1: Add Server (1-time setup)
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📌 Method 1: Add Server in Minecraft (One-Time Permanent Setup)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "1. Start proxy above and tap 'Copy 127.0.0.1'.\n" +
                                    "2. In Minecraft: Tap Play.\n" +
                                    "3. In New 1.21+ UI: Tap Servers icon (stacked cubes 🖥️ on left sidebar) ➡️ Scroll to bottom & tap 'Add Server'.\n" +
                                    "   (In Classic UI: Tap 'Servers' tab at the top ➡️ Add Server).\n" +
                                    "4. Name: AG Client | Server Address: 127.0.0.1 | Port: $localPort.\n" +
                                    "5. Tap Save & Join! (Since 127.0.0.1 is permanent, you never have to re-enter it!).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                // Method 2: Friends / LAN Games
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = AccentGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "⚡ Method 2: Friends & LAN Games (Zero Typing)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                        Text(
                            text = "• Open Minecraft ➡️ Tap Play.\n" +
                                    "• In New 1.21+ UI: Tap Friends icon (two player silhouettes 👥 on left sidebar) ➡️ Scroll to LAN Games.\n" +
                                    "  (In Classic UI: Tap 'Friends' tab at top ➡️ LAN Games).\n" +
                                    "• Tap 'AG Client v2.1' to connect instantly without typing any IP!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
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
