package com.aayaan.agclient.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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

data class FeaturedServerPreset(
    val name: String,
    val host: String,
    val port: Int = 19132,
    val icon: String
)

val POPULAR_FEATURED_SERVERS = listOf(
    FeaturedServerPreset("The Hive", "geo.hivebedrock.network", 19132, "🐝"),
    FeaturedServerPreset("NetherGames", "play.nethergames.org", 19132, "⚔️"),
    FeaturedServerPreset("CubeCraft", "play.cubecraft.net", 19132, "🧊"),
    FeaturedServerPreset("Lifeboat", "play.lbsg.net", 19132, "⛵"),
    FeaturedServerPreset("Zeqa", "zeqa.net", 19132, "🎯")
)

@Composable
fun QuickConnectCard(
    targetServer: String = "",
    targetPort: Int = 19132,
    localPort: Int = 19133,
    onSelectTarget: ((String, Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lunarDomain = "play.localtest.me"
    val alternativeDomain = "127.0.0.1.nip.io"

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

            // Upstream Target Info & Quick Server Chips
            Surface(
                color = DarkCard,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
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
                            text = if (targetServer.isNotBlank()) "Target Server: $targetServer:$targetPort" else "⚠️ No target server chosen! Tap one to play:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (targetServer.isNotBlank()) TextPrimary else AccentAmber
                        )
                    }

                    // Quick-Pick Preset Server Chips (Like Lunar Proxy)
                    if (onSelectTarget != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (preset in POPULAR_FEATURED_SERVERS) {
                                val isSelected = preset.host.equals(targetServer.trim(), ignoreCase = true)
                                Surface(
                                    color = if (isSelected) NeonCyan.copy(alpha = 0.2f) else DarkBackground,
                                    border = BorderStroke(1.dp, if (isSelected) NeonCyan else DarkCardBorder),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onSelectTarget(preset.host, preset.port)
                                            Toast.makeText(context, "Selected: ${preset.name}", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(text = preset.icon, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            text = preset.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) NeonCyan else TextSecondary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Lunar Proxy Trick Address Box (Website Domain Instead of Raw IP)
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Server Address (Website Name)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                Surface(
                                    color = ElectricPurple.copy(alpha = 0.25f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "⚡ Lunar Proxy Trick",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricPurple,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = lunarDomain,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = NeonCyan
                            )
                            Text(
                                text = "Bypasses Android's loopback bug by resolving via DNS",
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

                    HorizontalDivider(color = DarkCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Alternative Domain fallback:",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Text(
                            text = alternativeDomain,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            // Quick Action Buttons (Copy Domain & Copy Port)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Server Address", lunarDomain)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied Address: $lunarDomain", Toast.LENGTH_SHORT).show()
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
                    Text(text = "Copy Address", color = NeonCyan)
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

            // Connection Guide (Add Server - The Lunar Proxy Method)
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
                        text = "1. Start proxy above (make sure a target server like The Hive or NetherGames is selected).\n" +
                                "2. Tap 'Copy Address' and 'Launch Minecraft'.\n" +
                                "3. In Minecraft: Tap Play.\n" +
                                "4. In New 1.21+ UI: Tap Servers icon (stacked cubes 🖥️ on left sidebar) ➡️ Scroll to bottom & tap 'Add Server'.\n" +
                                "   (In Classic UI: Tap 'Servers' tab at the top ➡️ Add Server).\n" +
                                "5. Enter details:\n" +
                                "   • Server Name: AG Client\n" +
                                "   • Server Address: $lunarDomain\n" +
                                "   • Port: $localPort\n" +
                                "6. Tap Save & Join! (Saved in your server list forever).",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = "💡 Why this works: Just like Lunar Proxy, using a website address ($lunarDomain) bypasses the Android bug that blocks raw 127.0.0.1 IP connections.",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentGreen
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
