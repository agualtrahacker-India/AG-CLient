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
                currentConfig = gson.fromJson(json, ProxyConfig::class.java)
                Logger.i("ConfigManager", "Loaded configuration from ${file.name}")
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
}
