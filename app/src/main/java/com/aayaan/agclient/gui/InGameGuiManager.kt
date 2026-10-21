package com.aayaan.agclient.gui

import com.aayaan.agclient.gui.models.CustomForm
import com.aayaan.agclient.gui.models.FormButton
import com.aayaan.agclient.gui.models.FormElement
import com.aayaan.agclient.gui.models.SimpleForm
import com.aayaan.agclient.modules.Module
import com.aayaan.agclient.modules.ModuleCategory
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.modules.ModuleProperty
import com.aayaan.agclient.proxy.ProxySession
import com.aayaan.agclient.utils.Logger
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket
import org.cloudburstmc.protocol.common.PacketSignal
import java.util.concurrent.ConcurrentHashMap

class InGameGuiManager(private val moduleManager: ModuleManager) {

    private val gson = Gson()
    private val activeFormCategories = ConcurrentHashMap<Int, ModuleCategory>()

    companion object {
        const val MAIN_MENU_FORM_ID = 99001
        const val CATEGORY_BASE_FORM_ID = 99010
    }

    fun openMainMenu(session: ProxySession) {
        val buttons = ModuleCategory.values().map { category ->
            val count = moduleManager.getModulesByCategory(category).count { it.isEnabled }
            FormButton("${category.displayName} §7(Active: $count)§r")
        }

        val form = SimpleForm(
            title = "§b§lAG Client Beta§r",
            content = "§7Standalone Minecraft Bedrock Android Proxy\nSelect a category to configure modules:§r",
            buttons = buttons
        )

        sendForm(session, MAIN_MENU_FORM_ID, gson.toJson(form))
    }

    fun openCategoryMenu(session: ProxySession, category: ModuleCategory) {
        val formId = CATEGORY_BASE_FORM_ID + category.ordinal
        activeFormCategories[formId] = category

        val elements = mutableListOf<FormElement>()
        val modules = moduleManager.getModulesByCategory(category)

        elements.add(FormElement.Label("§e=== ${category.displayName} Modules ===§r"))

        for (module in modules) {
            elements.add(FormElement.Toggle("§b${module.name}§r (${module.description})", module.isEnabled))
            for (prop in module.properties) {
                when (prop) {
                    is ModuleProperty.BooleanProperty -> {
                        elements.add(FormElement.Toggle("  §7- ${prop.name}§r", prop.value))
                    }
                    is ModuleProperty.FloatProperty -> {
                        elements.add(FormElement.Slider("  §7- ${prop.name}§r", prop.min, prop.max, prop.step, prop.value))
                    }
                    is ModuleProperty.IntProperty -> {
                        elements.add(FormElement.Slider("  §7- ${prop.name}§r", prop.min.toFloat(), prop.max.toFloat(), 1f, prop.value.toFloat()))
                    }
                    is ModuleProperty.ModeProperty -> {
                        val selectedIdx = prop.options.indexOf(prop.value).coerceAtLeast(0)
                        elements.add(FormElement.Dropdown("  §7- ${prop.name}§r", prop.options, selectedIdx))
                    }
                    is ModuleProperty.StringProperty -> {
                        elements.add(FormElement.Input("  §7- ${prop.name}§r", default = prop.value))
                    }
                }
            }
        }

        val form = CustomForm(
            title = "§l${category.displayName} Config§r",
            content = elements
        )

        sendForm(session, formId, gson.toJson(form))
    }

    private fun sendForm(session: ProxySession, formId: Int, formDataJson: String) {
        val packet = ModalFormRequestPacket().apply {
            this.formId = formId
            this.formData = formDataJson
        }
        session.sendToClient(packet)
    }

    fun handleClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        if (packet !is ModalFormResponsePacket) return PacketSignal.UNHANDLED

        val id = packet.formId
        if (id !in 99001..99099) {
            return PacketSignal.UNHANDLED // Not our proxy modal form
        }

        // Cancel and drop so remote server never sees this form response
        val responseData = packet.formData
        if (responseData == null || responseData.trim() == "null" || responseData.trim().isEmpty()) {
            return PacketSignal.HANDLED
        }

        try {
            if (id == MAIN_MENU_FORM_ID) {
                val buttonIdx = responseData.trim().toIntOrNull()
                if (buttonIdx != null && buttonIdx in ModuleCategory.values().indices) {
                    val category = ModuleCategory.values()[buttonIdx]
                    openCategoryMenu(session, category)
                }
            } else if (id in CATEGORY_BASE_FORM_ID..(CATEGORY_BASE_FORM_ID + ModuleCategory.values().size)) {
                val category = activeFormCategories[id]
                if (category != null) {
                    applyCategoryFormResponse(category, responseData, session)
                }
            }
        } catch (e: Exception) {
            Logger.e("InGameGui", "Error parsing form response for ID $id", e)
        }

        return PacketSignal.HANDLED
    }

    private fun applyCategoryFormResponse(category: ModuleCategory, responseJson: String, session: ProxySession) {
        val jsonArray = JsonParser.parseString(responseJson).asJsonArray
        val modules = moduleManager.getModulesByCategory(category)

        var elementIndex = 1 // Skip initial label

        for (module in modules) {
            if (elementIndex < jsonArray.size()) {
                val moduleEnabled = jsonArray.get(elementIndex).asBoolean
                if (module.isEnabled != moduleEnabled) {
                    if (moduleEnabled) module.enable(session) else module.disable(session)
                }
                elementIndex++
            }

            for (prop in module.properties) {
                if (elementIndex < jsonArray.size()) {
                    val element = jsonArray.get(elementIndex)
                    when (prop) {
                        is ModuleProperty.BooleanProperty -> {
                            prop.value = element.asBoolean
                        }
                        is ModuleProperty.FloatProperty -> {
                            prop.value = element.asFloat
                        }
                        is ModuleProperty.IntProperty -> {
                            prop.value = element.asFloat.toInt()
                        }
                        is ModuleProperty.ModeProperty -> {
                            val idx = element.asInt
                            if (idx in prop.options.indices) {
                                prop.value = prop.options[idx]
                            }
                        }
                        is ModuleProperty.StringProperty -> {
                            prop.value = element.asString
                        }
                    }
                    elementIndex++
                }
            }
        }

        session.sendChatToClient("§a[Proxy] Updated settings for ${category.displayName}!§r")
    }
}
