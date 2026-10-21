package com.aayaan.agclient.modules.utility

import com.aayaan.agclient.modules.Module
import com.aayaan.agclient.modules.ModuleCategory
import com.aayaan.agclient.proxy.ProxySession

// 1. In-Game GUI
class GuiModule : Module(
    name = "InGameGUI",
    commandName = "gui",
    category = ModuleCategory.UTILITY,
    description = "Opens the native in-game Bedrock configuration modal form"
) {
    override fun onEnable(session: ProxySession?) {
        session ?: return
        session.guiManager.openMainMenu(session)
        disable(session)
    }
}

// 2. Config Module
class ConfigModule : Module(
    name = "Config",
    commandName = "config",
    category = ModuleCategory.UTILITY,
    description = "Saves and loads proxy profiles to internal JSON storage"
)

// 3. Ghost Mode
class GhostModule : Module(
    name = "Ghost",
    commandName = "ghost",
    category = ModuleCategory.UTILITY,
    description = "Hides all proxy chat output, commands, and overlays"
) {
    override fun onEnable(session: ProxySession?) {
        session?.config?.isGhostMode = true
    }

    override fun onDisable(session: ProxySession?) {
        session?.config?.isGhostMode = false
    }
}
