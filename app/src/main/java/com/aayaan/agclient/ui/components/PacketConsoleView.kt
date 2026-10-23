package com.aayaan.agclient.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.ui.theme.*
import com.aayaan.agclient.utils.LogEntry
import com.aayaan.agclient.utils.LogLevel
import com.aayaan.agclient.utils.Logger
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PacketConsoleView(
    modifier: Modifier = Modifier
) {
    val logs = remember { mutableStateListOf<LogEntry>() }
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Logger.logsFlow.collectLatest { entry ->
            logs.add(entry)
            if (logs.size > 300) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Packet & System Console",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonCyan
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val text = Logger.getAllLogsText()
                            clipboardManager.setText(AnnotatedString(text))
                            Toast.makeText(context, "Copied logs to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = DarkCard),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy Logs",
                            tint = NeonCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", color = NeonCyan, style = MaterialTheme.typography.labelSmall)
                    }

                    IconButton(
                        onClick = {
                            logs.clear()
                            Logger.clearLogs()
                            Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
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
