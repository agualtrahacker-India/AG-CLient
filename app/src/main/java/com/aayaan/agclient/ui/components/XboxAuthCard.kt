package com.aayaan.agclient.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aayaan.agclient.auth.XboxAuthState
import com.aayaan.agclient.ui.theme.*

@Composable
fun XboxAuthCard(
    authState: XboxAuthState,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
            Text(
                text = "Microsoft & Xbox Live Identity",
                style = MaterialTheme.typography.titleMedium,
                color = ElectricPurple
            )

            when (authState) {
                is XboxAuthState.Authenticated -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Gamertag: ${authState.gamertag}", style = MaterialTheme.typography.titleMedium, color = AccentGreen)
                            Text(text = "XUID: ${authState.xuid}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(
                            onClick = onLogoutClick,
                            colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                        ) {
                            Text(text = "Disconnect", color = AccentRed)
                        }
                    }
                }
                is XboxAuthState.DeviceCodePrompt -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Copy the code below, then open the Microsoft login page:",
                            style = MaterialTheme.typography.bodyMedium
                        )

                        // Big readable code card
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = authState.userCode,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        letterSpacing = 6.sp,
                                        color = NeonCyan
                                    )
                                )
                            }
                        }

                        // Action Buttons: Copy Code & Open Browser
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Xbox Login Code", authState.userCode)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Code copied: ${authState.userCode}", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCard)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Code", color = NeonCyan)
                            }

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authState.verificationUri)).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Failed to open browser: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                            ) {
                                Icon(Icons.Default.OpenInBrowser, contentDescription = "Open", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Link")
                            }
                        }

                        // Reassuring explanation
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBackground),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "ℹ️ Note: Microsoft will show 'Sign in to Prism Launcher'. This is completely safe — AG Client uses this trusted open-source client ID to authenticate your own personal account.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Waiting for you to complete sign-in in browser...",
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentAmber
                            )
                        }
                    }
                }
                is XboxAuthState.Authenticating -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp))
                        Text(text = "Connecting to Microsoft Identity...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is XboxAuthState.Error -> {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(text = "Auth Error: ${authState.error}", style = MaterialTheme.typography.bodyMedium, color = AccentRed)
                        Button(
                            onClick = onLoginClick,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                        ) {
                            Text("Retry Device Code Login")
                        }
                    }
                }
                is XboxAuthState.Idle -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Not logged in (using offline identity)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Button(
                            onClick = onLoginClick,
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                        ) {
                            Text("Login with Xbox")
                        }
                    }
                }
            }
        }
    }
}
