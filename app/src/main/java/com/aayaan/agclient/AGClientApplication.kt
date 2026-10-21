package com.aayaan.agclient

import android.app.Application
import com.aayaan.agclient.auth.XboxAuthManager
import com.aayaan.agclient.commands.CommandManager
import com.aayaan.agclient.config.ConfigManager
import com.aayaan.agclient.gui.InGameGuiManager
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.utils.Logger

class AGClientApplication : Application() {

    lateinit var xboxAuthManager: XboxAuthManager
        private set
    lateinit var configManager: ConfigManager
        private set
    lateinit var moduleManager: ModuleManager
        private set
    lateinit var guiManager: InGameGuiManager
        private set
    lateinit var commandManager: CommandManager
        private set

    override fun onCreate() {
        super.onCreate()
        Logger.i("AGClient", "Initializing AG Client Beta application...")

        xboxAuthManager = XboxAuthManager(this)
        configManager = ConfigManager(this)
        moduleManager = ModuleManager(configManager)
        guiManager = InGameGuiManager(moduleManager)
        commandManager = CommandManager(moduleManager, configManager, guiManager)

        Logger.i("AGClient", "AG Client Beta core subsystems initialized successfully.")
    }
}
