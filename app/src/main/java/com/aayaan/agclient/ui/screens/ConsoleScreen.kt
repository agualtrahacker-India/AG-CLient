package com.aayaan.agclient.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.ui.components.PacketConsoleView

@Composable
fun ConsoleScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Packet Pipeline Traffic",
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = "Real-time view of intercepted, modified, and forwarded Bedrock packets.",
            style = MaterialTheme.typography.bodyMedium
        )
        PacketConsoleView(modifier = Modifier.fillMaxSize())
    }
}
