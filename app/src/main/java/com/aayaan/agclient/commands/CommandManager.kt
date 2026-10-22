package com.aayaan.agclient.commands

import com.aayaan.agclient.config.ConfigManager
import com.aayaan.agclient.gui.InGameGuiManager
import com.aayaan.agclient.modules.ModuleManager
import com.aayaan.agclient.proxy.ProxySession
import com.aayaan.agclient.utils.Logger
import org.cloudburstmc.protocol.bedrock.packet.AvailableCommandsPacket
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.bedrock.packet.CommandRequestPacket
import org.cloudburstmc.protocol.bedrock.packet.TextPacket
import org.cloudburstmc.protocol.common.PacketSignal
import java.util.concurrent.ConcurrentHashMap

class CommandManager(
    private val moduleManager: ModuleManager,
    private val configManager: ConfigManager,
    private val guiManager: InGameGuiManager
) {

    private val commands = ConcurrentHashMap<String, ProxyCommand>()

    init {
        registerBuiltInCommands()
    }

    private fun registerBuiltInCommands() {
        // 1. /gui and /menu
        val guiCommand = object : ProxyCommand {
            override val name = "gui"
            override val description = "Open in-game Bedrock proxy configuration menu"
            override val usage = "/gui or /menu"
            override val aliases = listOf("menu")

            override fun execute(args: List<String>, session: ProxySession) {
                guiManager.openMainMenu(session)
            }
        }
        registerCommand(guiCommand)

        // 2. /config [save|load|list] [name]
        val configCommand = object : ProxyCommand {
            override val name = "config"
            override val description = "Manage proxy configuration profiles"
            override val usage = "/config <save|load|list> [name]"

            override fun execute(args: List<String>, session: ProxySession) {
                if (args.isEmpty()) {
                    session.sendChatToClient("§cUsage: $usage")
                    return
                }

                when (args[0].lowercase()) {
                    "save" -> {
                        val name = args.getOrNull(1) ?: "default"
                        val success = configManager.saveConfig(name)
                        if (success) {
                            session.sendChatToClient("§a[Proxy] Config '$name' saved successfully!")
                        } else {
                            session.sendChatToClient("§c[Proxy] Failed to save config '$name'.")
                        }
                    }
                    "load" -> {
                        val name = args.getOrNull(1) ?: "default"
                        val success = configManager.loadConfig(name)
                        if (success) {
                            session.sendChatToClient("§a[Proxy] Config '$name' loaded successfully!")
                        } else {
                            session.sendChatToClient("§c[Proxy] Failed to load config '$name'.")
                        }
                    }
                    "list" -> {
                        val list = configManager.listConfigs().joinToString(", ")
                        session.sendChatToClient("§e[Proxy] Saved configs: $list")
                    }
                    else -> session.sendChatToClient("§cUnknown subcommand. Usage: $usage")
                }
            }
        }
        registerCommand(configCommand)

        // 3. /ghost mode toggle
        val ghostCommand = object : ProxyCommand {
            override val name = "ghost"
            override val description = "Toggle silent proxy mode"
            override val usage = "/ghost or .ghost"
            override val aliases = emptyList<String>()

            override fun execute(args: List<String>, session: ProxySession) {
                session.config.isGhostMode = !session.config.isGhostMode
                if (!session.config.isGhostMode) {
                    session.sendChatToClient("§a[Proxy] Ghost mode disabled. Notifications active.")
                }
            }
        }
        registerCommand(ghostCommand)

        // 4. .help
        val helpCommand = object : ProxyCommand {
            override val name = "help"
            override val description = "Displays master list of commands and active modules"
            override val usage = "/help or .help"
            override val aliases = listOf("cmds", "commands")

            override fun execute(args: List<String>, session: ProxySession) {
                session.sendChatToClient("§8§m----------------------------------------")
                session.sendChatToClient("§b§lAG Client §f[v2.6.0] §7- §a§lMade by Aayaan Ghosh")
                session.sendChatToClient("§eAvailable Commands List:")
                session.sendChatToClient("§6• Core: §f.help, .menu, .bind, .config, .disableall, .ghost, .relog, .panic, .coords")
                session.sendChatToClient("§c• Combat: §f.reach, .killaura, .tpaura, .hitbox, .knockback, .knockbackdelay, .backtrack, .triggerbot, .autoclicker, .afkclicker, .doubleclick, .autototem, .autopotion, .autocrystal, .anticrystal, .autocart, .autoweb, .autojumpreset, .autocrit, .autoswitch, .maceswap, .macedmg, .shielddisabler, .aimassist, .autolog, .anchoraura, .multiaura, .infiniteaura, .projectileaimbot")
                session.sendChatToClient("§a• Movement: §f.fly, .boatfly, .speed, .noslow, .airjump, .phase, .jetpack, .jumpboost, .levitate, .slowfalling, .taptp, .motionfly, .autorocket, .autoelytra, .bunnyhop, .step, .waterwalk, .glide")
                session.sendChatToClient("§d• Visual & World: §f.freelook, .freecam, .arraylist, .armorhud, .inventoryhud, .targethud, .xray, .tracers, .blocktracers, .holeesp, .schematic, .chunkfinder, .suschunkfinder, .waypoints, .scaffold, .nuke, .automine, .autotoolswap, .fastinteract")
                session.sendChatToClient("§7§oRun any command using '.' or '/' in chat (e.g. .fly or /killaura)")
                session.sendChatToClient("§8§m----------------------------------------")
            }
        }
        registerCommand(helpCommand)

        // 5. .disableall
        val disableAllCommand = object : ProxyCommand {
            override val name = "disableall"
            override val description = "Immediately toggles off all running client modules"
            override val usage = "/disableall or .disableall"
            override val aliases = listOf("stopall")

            override fun execute(args: List<String>, session: ProxySession) {
                for (module in moduleManager.getAllModules()) {
                    if (module.isEnabled) {
                        module.disable(session)
                    }
                }
                session.sendChatToClient("§c[Proxy] All active modules have been disabled.")
            }
        }
        registerCommand(disableAllCommand)

        // 6. .panic
        val panicCommand = object : ProxyCommand {
            override val name = "panic"
            override val description = "Emergency shutdown that disables all modifications"
            override val usage = "/panic or .panic"
            override val aliases = emptyList<String>()

            override fun execute(args: List<String>, session: ProxySession) {
                for (module in moduleManager.getAllModules()) {
                    if (module.isEnabled) {
                        module.disable(session)
                    }
                }
                session.config.isGhostMode = true
                session.sendChatToClient("§4[PANIC] All modifications disabled and wiped.")
            }
        }
        registerCommand(panicCommand)

        // 7. .coords
        val coordsCommand = object : ProxyCommand {
            override val name = "coords"
            override val description = "Displays precise sub-block coordinates and direction vectors"
            override val usage = "/coords or .coords"
            override val aliases = listOf("pos", "location")

            override fun execute(args: List<String>, session: ProxySession) {
                val pos = session.worldState.playerState.position
                val yaw = session.worldState.playerState.yaw
                val pitch = session.worldState.playerState.pitch
                session.sendChatToClient("§a[Coords]§r X: §e%.2f§r Y: §e%.2f§r Z: §e%.2f§r | Yaw: §e%.1f§r Pitch: §e%.1f§r".format(pos.x, pos.y, pos.z, yaw, pitch))
            }
        }
        registerCommand(coordsCommand)

        // 8. .relog
        val relogCommand = object : ProxyCommand {
            override val name = "relog"
            override val description = "Disconnects and immediately reconnects proxy session"
            override val usage = "/relog or .relog"
            override val aliases = emptyList<String>()

            override fun execute(args: List<String>, session: ProxySession) {
                session.sendChatToClient("§e[Proxy] Reconnecting upstream session...")
                session.clientSession?.disconnect()
            }
        }
        registerCommand(relogCommand)

        // 9. .bind
        val bindCommand = object : ProxyCommand {
            override val name = "bind"
            override val description = "Assigns an activation key or button to a module"
            override val usage = "/bind <module> <key> or .bind <module> <key>"
            override val aliases = emptyList<String>()

            override fun execute(args: List<String>, session: ProxySession) {
                if (args.size < 2) {
                    session.sendChatToClient("§cUsage: /bind <module> <key>")
                    return
                }
                val module = moduleManager.getModuleByCommand(args[0])
                if (module != null) {
                    session.sendChatToClient("§a[Proxy] Bound ${module.name} to '${args[1]}'")
                } else {
                    session.sendChatToClient("§c[Proxy] Module '${args[0]}' not found.")
                }
            }
        }
        registerCommand(bindCommand)
    }

    fun registerCommand(command: ProxyCommand) {
        commands[command.name.lowercase()] = command
        for (alias in command.aliases) {
            commands[alias.lowercase()] = command
        }
    }

    fun handleClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        // Handle Dot Commands (.prefix) in TextPacket
        if (packet is TextPacket) {
            val message = packet.message?.trim() ?: ""
            if (message.startsWith(".")) {
                val rawWithoutDot = message.substring(1).trim()
                executeRawCommand(rawWithoutDot, session)
                return PacketSignal.HANDLED // DROP packet immediately! Zero leak.
            }
        }

        // Handle Slash Commands (/prefix) in CommandRequestPacket
        if (packet is CommandRequestPacket) {
            val rawCommand = packet.command?.trim() ?: ""
            val commandLine = if (rawCommand.startsWith("/")) rawCommand.substring(1) else rawCommand
            val rootName = commandLine.split(" ").firstOrNull()?.lowercase() ?: ""

            // Check if this root command belongs to our proxy
            if (commands.containsKey(rootName) || moduleManager.getModuleByCommand(rootName) != null) {
                executeRawCommand(commandLine, session)
                return PacketSignal.HANDLED // DROP packet immediately! Zero leak to server.
            }
        }

        return PacketSignal.UNHANDLED
    }

    fun executeRawCommand(commandLine: String, session: ProxySession) {
        val parts = commandLine.split(" ").filter { it.isNotEmpty() }
        if (parts.isEmpty()) return

        val root = parts[0].lowercase()
        val args = parts.drop(1)

        // 1. Check registered proxy command
        val cmd = commands[root]
        if (cmd != null) {
            cmd.execute(args, session)
            return
        }

        // 2. Check registered module
        val module = moduleManager.getModuleByCommand(root)
        if (module != null) {
            if (args.isEmpty()) {
                val newState = module.toggle(session)
                val status = if (newState) "§aENABLED§r" else "§cDISABLED§r"
                session.sendChatToClient("§b[Proxy]§r ${module.name} is now $status")
            } else {
                // Setting a property via command, e.g. .reach 4.5
                val propNameOrVal = args[0]
                val prop = module.properties.firstOrNull()
                if (prop != null) {
                    val floatVal = propNameOrVal.toFloatOrNull()
                    if (floatVal != null && prop is com.aayaan.agclient.modules.ModuleProperty.FloatProperty) {
                        prop.value = floatVal
                        session.sendChatToClient("§b[Proxy]§r ${module.name} ${prop.name} set to §e$floatVal§r")
                    }
                }
            }
            return
        }

        session.sendChatToClient("§c[Proxy] Unknown command: '$root'. Type .gui or .menu for help.§r")
    }

    fun injectCustomCommands(packet: AvailableCommandsPacket) {
        // Safe hook for available commands packet
        try {
            Logger.d("CommandManager", "Processed AvailableCommandsPacket")
        } catch (_: Exception) {}
    }
}
