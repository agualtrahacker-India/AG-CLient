package com.aayaan.agclient.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.config.ConfigManager
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.ui.theme.*

@Composable
fun SettingsScreen(
    configManager: ConfigManager,
    config: ProxyConfig,
    onConfigUpdated: () -> Unit,
    modifier: Modifier = Modifier
) {
    var saveName by remember { mutableStateOf("") }
    var savedList by remember { mutableStateOf(configManager.listConfigs()) }
    var ghostMode by remember { mutableStateOf(config.isGhostMode) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = "Proxy Settings & Profiles", style = MaterialTheme.typography.titleLarge)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text = "Ghost Mode (Silent Proxy)", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Suppress [Proxy] chat messages and hide proxy commands from in-game autocompletion.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = ghostMode,
                        onCheckedChange = {
                            ghostMode = it
                            config.isGhostMode = it
                        }
                    )
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, DarkCardBorder, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(text = "Profile Management", style = MaterialTheme.typography.titleMedium, color = ElectricPurple)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = saveName,
                        onValueChange = { saveName = it },
                        label = { Text("Profile Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (saveName.isNotBlank()) {
                                configManager.saveConfig(saveName.trim())
                                savedList = configManager.listConfigs()
                                saveName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                    ) {
                        Text("Save", color = DarkBackground)
                    }
                }

                Text(text = "Saved Profiles:", style = MaterialTheme.typography.bodyMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedList) { name ->
                        SuggestionChip(
                            onClick = {
                                configManager.loadConfig(name)
                                onConfigUpdated()
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }
        }
    }
}
