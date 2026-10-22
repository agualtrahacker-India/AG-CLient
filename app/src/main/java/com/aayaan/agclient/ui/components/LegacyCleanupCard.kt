package com.aayaan.agclient.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.ui.theme.*

@Composable
fun LegacyCleanupCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var isLegacyInstalled by remember {
        mutableStateOf(checkLegacyInstalled(context))
    }

    if (!isLegacyInstalled) return

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, AccentAmber.copy(alpha = 0.8f), RoundedCornerShape(12.dp)),
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
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = "Warning",
                    tint = AccentAmber,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = "Legacy Duplicate Version Detected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AccentAmber
                )
            }

            Text(
                text = "An older debug build (com.aayaan.agclient.debug) was found on your phone. Android keeps both versions as separate duplicate icons.\n\nTap below to cleanly remove the older duplicate — all your server history and settings in this new v2.6.0 release are safe.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )

            Button(
                onClick = {
                    try {
                        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:com.aayaan.agclient.debug")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(uninstallIntent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not launch uninstaller: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = DarkBackground,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Uninstall Legacy Duplicate App",
                    color = DarkBackground,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun checkLegacyInstalled(context: Context): Boolean {
    return try {
        context.packageManager.getPackageInfo("com.aayaan.agclient.debug", 0)
        true
    } catch (_: Exception) {
        false
    }
}
