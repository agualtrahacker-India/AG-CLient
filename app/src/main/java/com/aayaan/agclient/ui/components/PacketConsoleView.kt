package com.aayaan.agclient.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.AGClientApplication
import com.aayaan.agclient.ui.theme.*
import com.aayaan.agclient.utils.ConnectionDiagnostics
import com.aayaan.agclient.utils.LogEntry
import com.aayaan.agclient.utils.LogLevel
import com.aayaan.agclient.utils.Logger
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun PacketConsoleView(
    modifier: Modifier = Modifier
) {
    val logs = remember { mutableStateListOf<LogEntry>() }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isRunningDiag by remember { mutableStateOf(false) }
    var isRunningAuthDiag by remember { mutableStateOf(false) }
    var lastDiagSummary by remember { mutableStateOf("") }
    var selectedFeedback by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        Logger.logsFlow.collectLatest { entry ->
            logs.add(entry)
            if (logs.size > 800) {
                logs.removeAt(0)
            }
            if (logs.isNotEmpty()) {
                listState.scrollToItem(logs.size - 1)
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = DarkBackground),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Terminal, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Diagnostics & Packet Console",
                        style = MaterialTheme.typography.titleMedium,
                        color = NeonCyan
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val text = Logger.buildComprehensiveReport(lastDiagSummary, selectedFeedback)
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Full diagnostic bundle copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Report",
                            tint = NeonCyan,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Report", color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                    }

                    IconButton(
                        onClick = {
                            logs.clear()
                            Logger.clearLogs()
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Clear Logs",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Diagnostic & Feedback Action Controls
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Network Diagnostic
                    Button(
                        onClick = {
                            if (isRunningDiag) return@Button
                            isRunningDiag = true
                            coroutineScope.launch {
                                Logger.diag("Running Full Connectivity & Environment Diagnostics...")
                                val app = context.applicationContext as? AGClientApplication
                                val cfg = app?.configManager?.currentConfig ?: com.aayaan.agclient.config.ProxyConfig()
                                val result = ConnectionDiagnostics.runFullDiagnostics(context, cfg)
                                lastDiagSummary = result.summary
                                Logger.diag("Diagnostic Results:\n${result.summary}")
                                isRunningDiag = false
                                Toast.makeText(context, "Network diagnostics complete!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRunningDiag) DarkCard else ElectricPurple),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        enabled = !isRunningDiag
                    ) {
                        if (isRunningDiag) {
                            CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing...", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Network Diagnostic", color = TextPrimary, style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    // 2. Auth Diagnostic
                    Button(
                        onClick = {
                            if (isRunningAuthDiag) return@Button
                            isRunningAuthDiag = true
                            coroutineScope.launch {
                                Logger.diag("Running Microsoft & Xbox Auth Diagnostics...")
                                val app = context.applicationContext as? AGClientApplication
                                val authMgr = app?.xboxAuthManager
                                val result = authMgr?.runAuthDiagnostic() ?: "AuthManager not available"
                                Logger.diag(result)
                                isRunningAuthDiag = false
                                Toast.makeText(context, "Auth diagnostics complete!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRunningAuthDiag) DarkCard else NeonCyan),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        enabled = !isRunningAuthDiag
                    ) {
                        if (isRunningAuthDiag) {
                            CircularProgressIndicator(color = DarkBackground, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Testing Auth...", color = DarkBackground, style = MaterialTheme.typography.labelSmall)
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Auth Diagnostic", color = DarkBackground, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    Text(
                        text = "Branch: connection-test",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentAmber
                    )
                }

                // User Connection Response / Feedback Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Report Result:", style = MaterialTheme.typography.labelSmall, color = TextSecondary)

                    val feedbackOptions = listOf(
                        "✅ Working" to "Working in Game",
                        "❌ Stuck: External Server" to "Stuck on Connecting to External Server",
                        "⚠️ Stuck: Multiplayer" to "Stuck on Connecting to Multiplayer Game",
                        "⏱️ Timed Out" to "Connection Timed Out"
                    )

                    for ((label, description) in feedbackOptions) {
                        val isSelected = selectedFeedback == description
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedFeedback = description
                                Logger.feedback("User reported connection status: $description")
                                Toast.makeText(context, "Recorded: $label", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DarkCard,
                                selectedLabelColor = NeonCyan,
                                containerColor = DarkBackground,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Console Log View
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 280.dp, max = 520.dp)
                    .background(DarkSurface, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(logs) { entry ->
                    val color = when (entry.level) {
                        LogLevel.PACKET -> NeonCyan
                        LogLevel.TUNNEL -> ElectricPurple
                        LogLevel.RELAY -> AccentGreen
                        LogLevel.UPSTREAM -> NeonCyanDark
                        LogLevel.DIAG -> AccentAmber
                        LogLevel.FEEDBACK -> AccentGreen
                        LogLevel.INFO -> AccentGreen
                        LogLevel.WARN -> AccentAmber
                        LogLevel.ERROR -> AccentRed
                        LogLevel.DEBUG -> TextSecondary
                    }
                    Text(
                        text = "[${entry.timestamp}] [${entry.tag}] ${entry.message}",
                        style = MaterialTheme.typography.labelSmall,
                        color = color
                    )
                }
            }
        }
    }
}
