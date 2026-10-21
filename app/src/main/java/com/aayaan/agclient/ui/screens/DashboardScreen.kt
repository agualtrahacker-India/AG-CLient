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
import com.aayaan.agclient.ui.components.ServerConfigCard
import com.aayaan.agclient.ui.components.ServiceControlCard
import com.aayaan.agclient.ui.components.XboxAuthCard

@Composable
fun DashboardScreen(
    config: ProxyConfig,
    onConfigChanged: (ProxyConfig) -> Unit,
    serviceState: ServiceState,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    authManager: XboxAuthManager,
    modifier: Modifier = Modifier
) {
    val authState by authManager.authState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ServiceControlCard(
            serviceState = serviceState,
            onStartService = onStartService,
            onStopService = onStopService
        )

        ServerConfigCard(
            config = config,
            onConfigChanged = onConfigChanged
        )

        XboxAuthCard(
            authState = authState,
            onLoginClick = { authManager.startDeviceCodeLogin() },
            onLogoutClick = { authManager.logout() }
        )
    }
}
