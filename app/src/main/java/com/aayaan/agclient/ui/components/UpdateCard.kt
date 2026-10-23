package com.aayaan.agclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.ui.theme.*
import com.aayaan.agclient.updater.UpdateState

@Composable
fun UpdateCard(
    updateState: UpdateState,
    onDownloadUpdate: (String) -> Unit,
    onInstallApk: (java.io.File) -> Unit,
    onCheckUpdate: () -> Unit = {},
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Updater", tint = NeonCyan)
                    Text(
                        text = "Auto-Updater (GitHub)",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DarkBackground,
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Text(
                        text = "Every 30m",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            when (updateState) {
                is UpdateState.Idle -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Version: 2.7.0 (Active)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Background auto-check runs every 30 minutes from app open.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                is UpdateState.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Checking GitHub Releases in background...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                is UpdateState.UpToDate -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                            Text(
                                text = "AG Client is up to date (${updateState.currentVersion})",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AccentGreen
                            )
                        }
                        Text(
                            text = "Next background check in 30 minutes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                is UpdateState.UpdateAvailable -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "⚡ New Update Available: v${updateState.version}",
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentGreen
                        )
                        if (updateState.releaseNotes.isNotBlank()) {
                            Text(
                                text = updateState.releaseNotes.take(150),
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Button(
                            onClick = { onDownloadUpdate(updateState.downloadUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update App (Download v${updateState.version})", color = DarkBackground)
                        }
                    }
                }
                is UpdateState.Downloading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Downloading APK update: ${(updateState.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonCyan
                        )
                        LinearProgressIndicator(
                            progress = updateState.progress,
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonCyan,
                            trackColor = DarkCard
                        )
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Button(
                        onClick = { onInstallApk(updateState.apkFile) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                    ) {
                        Text("Install Downloaded APK", color = DarkBackground)
                    }
                }
                is UpdateState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentRed
                        )
                        Text(
                            text = "Auto-checker will retry automatically in 30 minutes.",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}
