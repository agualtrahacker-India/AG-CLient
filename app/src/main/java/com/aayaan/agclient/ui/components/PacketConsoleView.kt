package com.aayaan.agclient.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

    LaunchedEffect(Unit) {
        Logger.logsFlow.collectLatest { entry ->
            logs.add(entry)
            if (logs.size > 200) {
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
            Text(
                text = "Live Packet & System Console",
                style = MaterialTheme.typography.titleMedium,
                color = NeonCyan
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
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
