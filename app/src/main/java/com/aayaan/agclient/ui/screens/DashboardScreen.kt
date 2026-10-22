package com.aayaan.agclient.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aayaan.agclient.auth.XboxAuthManager
import com.aayaan.agclient.auth.XboxAuthState
import com.aayaan.agclient.config.ProxyConfig
import com.aayaan.agclient.proxy.ServiceState
import com.aayaan.agclient.ui.components.LegacyCleanupCard
import com.aayaan.agclient.ui.components.QuickConnectCard
import com.aayaan.agclient.ui.components.ServerConfigCard
import com.aayaan.agclient.ui.components.ServiceControlCard
import com.aayaan.agclient.ui.components.UpdateCard
import com.aayaan.agclient.ui.components.XboxAuthCard
import com.aayaan.agclient.updater.AutoUpdater

@Composable
fun DashboardScreen(
    config: ProxyConfig,
    onConfigChanged: (ProxyConfig) -> Unit,
    serviceState: ServiceState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    isSandboxRunning: Boolean = false,
    onToggleSandbox: () -> Unit = {},
    authManager: XboxAuthManager,
    autoUpdater: AutoUpdater,
    modifier: Modifier = Modifier
) {
    val authState by authManager.authState.collectAsState()
    val updateState by autoUpdater.updateState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LegacyCleanupCard()

        ServiceControlCard(
            serviceState = serviceState,
            onStartService = onStartService,
            onStopService = onStopService,
            isSandboxRunning = isSandboxRunning,
            onToggleSandbox = onToggleSandbox,
            localPort = config.localBindPort
        )

        QuickConnectCard(
            targetServer = config.targetIp,
            targetPort = config.targetPort,
            localPort = config.localBindPort,
            isSandboxRunning = isSandboxRunning
        )

        UpdateCard(
            updateState = updateState,
            onDownloadUpdate = { url -> autoUpdater.startDownload(url) },
            onInstallApk = { apk -> autoUpdater.installApk(apk) }
        )

        ServerConfigCard(
            config = config,
            onConfigChanged = onConfigChanged
        )

        XboxAuthCard(
            authState = authState,
            currentIp = if (isSandboxRunning) "10.0.0.1" else "127.0.0.1",
            localPort = config.localBindPort,
            onLoginClick = { authManager.startDeviceCodeLogin() },
            onLogoutClick = { authManager.logout() }
        )
    }
}
