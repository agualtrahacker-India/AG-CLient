package com.aayaan.agclient.config

import android.content.Context
import com.aayaan.agclient.utils.Logger
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

class ConfigManager(private val context: Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val configDir: File by lazy {
        File(context.filesDir, "configs").apply { if (!exists()) mkdirs() }
    }

    var currentConfig: ProxyConfig = ProxyConfig()
        private set

    init {
        loadConfig("default")
    }

    fun saveConfig(name: String = "default"): Boolean {
        return try {
            val file = File(configDir, "$name.json")
            val json = gson.toJson(currentConfig)
            file.writeText(json)
            Logger.i("ConfigManager", "Configuration saved successfully to ${file.name}")
            true
        } catch (e: Exception) {
            Logger.e("ConfigManager", "Failed to save configuration '$name'", e)
            false
        }
    }

    fun loadConfig(name: String = "default"): Boolean {
        return try {
            val file = File(configDir, "$name.json")
            if (file.exists()) {
                val json = file.readText()
                val loaded = gson.fromJson(json, ProxyConfig::class.java)
                if (loaded != null) {
                    currentConfig = loaded.copy(
                        serverHistory = loaded.serverHistory ?: mutableListOf(),
                        moduleStates = loaded.moduleStates ?: mutableMapOf(),
                        moduleProperties = loaded.moduleProperties ?: mutableMapOf()
                    )
                } else {
                    currentConfig = ProxyConfig()
                }
                Logger.i("ConfigManager", "Loaded configuration from ${file.name} (${currentConfig.serverHistory.size} servers in history)")
                true
            } else {
                currentConfig = ProxyConfig()
                saveConfig(name)
                true
            }
        } catch (e: Exception) {
            Logger.e("ConfigManager", "Failed to load config '$name', resetting to defaults", e)
            currentConfig = ProxyConfig()
            false
        }
    }

    fun listConfigs(): List<String> {
        return configDir.listFiles { file -> file.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?: listOf("default")
    }

    fun deleteConfig(name: String): Boolean {
        if (name == "default") return false
        val file = File(configDir, "$name.json")
        return file.delete()
    }

    fun addServerToHistory(ip: String, port: Int = 19132, name: String = "") {
        val trimmedIp = ip.trim()
        if (trimmedIp.isBlank()) return

        val existingIndex = currentConfig.serverHistory.indexOfFirst {
            it.ip.equals(trimmedIp, ignoreCase = true) && it.port == port
        }
        if (existingIndex >= 0) {
            val existing = currentConfig.serverHistory.removeAt(existingIndex)
            val displayName = if (name.isNotBlank()) name else existing.name
            currentConfig.serverHistory.add(0, existing.copy(name = displayName, lastJoined = System.currentTimeMillis()))
        } else {
            val displayName = if (name.isNotBlank()) name else trimmedIp
            currentConfig.serverHistory.add(0, ServerHistoryItem(ip = trimmedIp, port = port, name = displayName, lastJoined = System.currentTimeMillis()))
        }
        saveConfig("default")
        Logger.i("ConfigManager", "Added $trimmedIp:$port to server history.")
    }

    fun removeServerFromHistory(ip: String, port: Int = 19132) {
        currentConfig.serverHistory.removeAll {
            it.ip.equals(ip.trim(), ignoreCase = true) && it.port == port
        }
        saveConfig("default")
        Logger.i("ConfigManager", "Removed $ip:$port from server history.")
    }
}
