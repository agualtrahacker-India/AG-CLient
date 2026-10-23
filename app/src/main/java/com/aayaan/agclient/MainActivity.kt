package com.aayaan.agclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.aayaan.agclient.utils.Logger
import com.aayaan.agclient.proxy.AGVirtualSandboxService
import com.aayaan.agclient.proxy.BedrockProxyService
import com.aayaan.agclient.proxy.ServiceState
import com.aayaan.agclient.ui.screens.*
import com.aayaan.agclient.ui.theme.*

enum class AppTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Dashboard", Icons.Default.Dashboard),
    MODULES("Modules", Icons.Default.Tune),
    CONSOLE("Console", Icons.Default.Terminal),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    private val vpnPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startVirtualSandbox()
            } else {
                Toast.makeText(this, "Virtual Sandbox requires VPN permission to auto-route Minecraft.", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val app = application as AGClientApplication

        // Check for updates quietly in background on launch
        app.autoUpdater.checkForUpdates(silent = true)

        setContent {
            AGClientTheme {
                val serviceState by BedrockProxyService.serviceState.collectAsState()
                val isSandboxRunning by AGVirtualSandboxService.isSandboxRunning.collectAsState()
                var currentTab by remember { mutableStateOf(AppTab.DASHBOARD) }
                var config by remember { mutableStateOf(app.configManager.currentConfig) }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "AG Client",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = NeonCyan
                                    )
                                    Badge(containerColor = ElectricPurple) {
                                        Text(
                                            text = "v2.8.1",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
                        )
                    },
                    bottomBar = {
                        NavigationBar(containerColor = DarkSurface) {
                            for (tab in AppTab.values()) {
                                NavigationBarItem(
                                    selected = currentTab == tab,
                                    onClick = { currentTab = tab },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = DarkBackground,
                                        selectedTextColor = NeonCyan,
                                        indicatorColor = NeonCyan,
                                        unselectedIconColor = TextSecondary,
                                        unselectedTextColor = TextSecondary
                                    )
                                )
                            }
                        }
                    },
                    containerColor = DarkBackground
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .background(DarkBackground)
                    ) {
                        when (currentTab) {
                            AppTab.DASHBOARD -> DashboardScreen(
                                config = config,
                                onConfigChanged = { updated ->
                                    config = updated
                                    app.configManager.saveConfig("default")
                                },
                                serviceState = serviceState,
                                onStartService = { startProxyService() },
                                onStopService = { stopProxyService() },
                                isSandboxRunning = isSandboxRunning,
                                onToggleSandbox = { toggleVirtualSandbox() },
                                authManager = app.xboxAuthManager,
                                autoUpdater = app.autoUpdater
                            )
                            AppTab.MODULES -> ModulesScreen(
                                moduleManager = app.moduleManager
                            )
                            AppTab.CONSOLE -> ConsoleScreen()
                            AppTab.SETTINGS -> SettingsScreen(
                                configManager = app.configManager,
                                config = config,
                                onConfigUpdated = {
                                    config = app.configManager.currentConfig
                                    app.moduleManager.syncWithConfig()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun startProxyService() {
        try {
            val intent = Intent(this, BedrockProxyService::class.java).apply {
                action = BedrockProxyService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (t: Throwable) {
            Logger.e("MainActivity", "Failed to start proxy service", t)
            Toast.makeText(this, "Failed to start proxy: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopProxyService() {
        try {
            val intent = Intent(this, BedrockProxyService::class.java).apply {
                action = BedrockProxyService.ACTION_STOP
            }
            startService(intent)
        } catch (t: Throwable) {
            Logger.e("MainActivity", "Failed to stop proxy service", t)
        }
    }

    private fun toggleVirtualSandbox() {
        if (AGVirtualSandboxService.isSandboxRunning.value) {
            stopVirtualSandbox()
        } else {
            val prepareIntent = AGVirtualSandboxService.getPrepareIntent(this)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                startVirtualSandbox()
            }
        }
    }

    private fun startVirtualSandbox() {
        try {
            if (BedrockProxyService.serviceState.value == ServiceState.STOPPED || BedrockProxyService.serviceState.value == ServiceState.ERROR) {
                startProxyService()
            }
            val intent = Intent(this, AGVirtualSandboxService::class.java).apply {
                action = AGVirtualSandboxService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        } catch (t: Throwable) {
            Logger.e("MainActivity", "Failed to start Virtual Sandbox", t)
            Toast.makeText(this, "Failed to start sandbox: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVirtualSandbox() {
        try {
            val intent = Intent(this, AGVirtualSandboxService::class.java).apply {
                action = AGVirtualSandboxService.ACTION_STOP
            }
            startService(intent)
        } catch (t: Throwable) {
            Logger.e("MainActivity", "Failed to stop Virtual Sandbox", t)
        }
    }
}
