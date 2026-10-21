package com.aayaan.agclient.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
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
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (String) -> Unit,
    onInstallApk: (java.io.File) -> Unit,
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

                IconButton(onClick = onCheckUpdate) {
                    Icon(Icons.Default.Refresh, contentDescription = "Check for Updates", tint = TextSecondary)
                }
            }

            when (updateState) {
                is UpdateState.Idle -> {
                    Text(
                        text = "Version: 1.0.0-beta (Latest GitHub Release Tracking)",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = onCheckUpdate,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                    ) {
                        Text("Check for Updates")
                    }
                }
                is UpdateState.Checking -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(20.dp))
                        Text(text = "Checking GitHub Releases...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is UpdateState.UpToDate -> {
                    Text(
                        text = "✓ AG Client is up to date (${updateState.currentVersion})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AccentGreen
                    )
                }
                is UpdateState.UpdateAvailable -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "⚡ New Update Available: v${updateState.version}",
                            style = MaterialTheme.typography.titleMedium,
                            color = AccentGreen
                        )
                        if (updateState.releaseNotes.isNotEmpty()) {
                            Text(
                                text = updateState.releaseNotes.take(150),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Button(
                            onClick = { onDownloadUpdate(updateState.downloadUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = DarkBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download & Install Update", color = DarkBackground)
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
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = updateState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AccentRed
                        )
                        OutlinedButton(
                            onClick = onCheckUpdate,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
                        ) {
                            Text("Retry Check")
                        }
                    }
                }
            }
        }
    }
}
