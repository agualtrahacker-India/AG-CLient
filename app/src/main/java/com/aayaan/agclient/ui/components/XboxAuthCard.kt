package com.aayaan.agclient.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aayaan.agclient.auth.XboxAuthState
import com.aayaan.agclient.ui.theme.*

@Composable
fun XboxAuthCard(
    authState: XboxAuthState? = null,
    currentIp: String = "10.0.0.1",
    localPort: Int = 19133,
    daysRemaining: Int = 90,
    onLoginClick: () -> Unit = {},
    onLogoutClick: () -> Unit = {},
    onCancelClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = {
                Text("Remove Xbox Account", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            },
            text = {
                Text(
                    "Are you sure you want to remove this Microsoft/Xbox account from AG Client? You can sign in again anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveDialog = false
                        onLogoutClick()
                        Toast.makeText(context, "Account removed.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
                ) {
                    Text("Remove", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkCard
        )
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ElectricPurple,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = "Microsoft & Xbox Live Auth",
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricPurple
                    )
                }

                // Status chip
                val statusText = when (authState) {
                    is XboxAuthState.Authenticated -> "Signed In"
                    is XboxAuthState.DeviceCodePrompt -> "Action Needed"
                    is XboxAuthState.Authenticating -> "Connecting..."
                    else -> "Guest / Passthrough"
                }
                val statusColor = when (authState) {
                    is XboxAuthState.Authenticated -> AccentGreen
                    is XboxAuthState.DeviceCodePrompt -> AccentAmber
                    is XboxAuthState.Authenticating -> NeonCyan
                    else -> TextSecondary
                }
                Surface(
                    color = DarkBackground,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            when (authState) {
                is XboxAuthState.Authenticated -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
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
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        text = authState.gamertag,
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "XUID: ${authState.xuid}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }

                            Text(
                                text = "• 90-Day session active ($daysRemaining days remaining with auto-refresh).\n• Official Mojang signature active for The Hive & featured servers.\n• Your exact custom skin, cape, and 3D model are automatically preserved and forwarded.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            OutlinedButton(
                                onClick = { showRemoveDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                                border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().height(36.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Remove / Sign Out Account", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                is XboxAuthState.DeviceCodePrompt -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Link your Microsoft account to authenticate with The Hive:",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary
                            )

                            // Big User Code Box
                            Surface(
                                color = DarkBackground,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "YOUR LINKING CODE:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                    Text(
                                        text = authState.userCode,
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = NeonCyan,
                                        letterSpacing = 2.sp
                                    )
                                }
                            }

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(authState.userCode))
                                        Toast.makeText(context, "Code copied: ${authState.userCode}", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Code", style = MaterialTheme.typography.labelSmall)
                                }

                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authState.verificationUri)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        try {
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Open Link", color = DarkBackground, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(authState.verificationUri))
                                        Toast.makeText(context, "Link copied!", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextSecondary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy Link (${authState.verificationUri})", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                }

                                TextButton(
                                    onClick = onCancelClick,
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("Cancel", style = MaterialTheme.typography.labelSmall, color = AccentRed)
                                }
                            }
                        }
                    }
                }

                is XboxAuthState.Authenticating -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = NeonCyan,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Contacting Microsoft & Xbox Live...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }

                else -> {
                    // Idle or Error
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkCard),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (authState is XboxAuthState.Error) {
                                Surface(
                                    color = DarkBackground,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, AccentRed.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = authState.error,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentRed,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Sign in with your Microsoft account once to play on The Hive & featured servers without 'Authentication Required' kicks. Your in-game skin & model are automatically preserved.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )

                            Button(
                                onClick = onLoginClick,
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple),
                                modifier = Modifier.fillMaxWidth().height(38.dp)
                            ) {
                                Icon(Icons.Default.SportsEsports, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Sign In with Microsoft", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                }
            }
        }
    }
}
