package com.aayaan.agclient.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "1. Open ${authState.verificationUri}\n2. Enter code below:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkCard),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = authState.userCode,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    letterSpacing = 4.sp,
                                    color = NeonCyan
                                ),
                                modifier = Modifier
                                    .padding(12.dp)
                                    .align(Alignment.CenterHorizontally)
                            )
                        }
                        Text(
                            text = "Waiting for Microsoft browser authentication...",
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentAmber
                        )
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
