package com.aayaan.agclient.modules

import com.aayaan.agclient.config.ConfigManager
import com.aayaan.agclient.modules.combat.*
import com.aayaan.agclient.modules.movement.*
import com.aayaan.agclient.modules.utility.*
import com.aayaan.agclient.modules.visual.*
import com.aayaan.agclient.modules.world.*
import com.aayaan.agclient.proxy.ProxySession
import com.aayaan.agclient.utils.Logger
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket
import org.cloudburstmc.protocol.common.PacketSignal
import java.util.concurrent.CopyOnWriteArrayList

class ModuleManager(private val configManager: ConfigManager) {

    private val modules = CopyOnWriteArrayList<Module>()

    init {
        registerAllModules()
        syncWithConfig()
    }

    private fun registerAllModules() {
        // Combat (13)
        register(KillAuraModule())
        register(ReachModule())
        register(HitboxModule())
        register(AutoClickerModule())
        register(TriggerBotModule())
        register(TpAuraModule())
        register(AutoCrystalModule())
        register(AnchorAuraModule())
        register(AutoTotemModule())
        register(KnockbackModule())
        register(BacktrackModule())
        register(ShieldDisablerModule())
        register(MaceSwapModule())

        // Movement (9)
        register(FlyModule())
        register(SpeedModule())
        register(PhaseModule())
        register(JetpackModule())
        register(BoatFlyModule())
        register(NoSlowModule())
        register(StepModule())
        register(WaterWalkModule())
        register(AirJumpModule())

        // Visual (7)
        register(XrayModule())
        register(PlayerEspModule())
        register(ItemEspModule())
        register(TracersModule())
        register(FreecamModule())
        register(ChunkFinderModule())
        register(HoleEspModule())
        register(NoInvisibleModule())

        // World (5)
        register(ScaffoldModule())
        register(NukeModule())
        register(AutoMineModule())
        register(AutoToolSwapModule())
        register(FastInteractModule())

        // Utility (3)
        register(GuiModule())
        register(ConfigModule())
        register(GhostModule())
    }

    fun register(module: Module) {
        modules.add(module)
    }

    fun getAllModules(): List<Module> = modules

    fun getModulesByCategory(category: ModuleCategory): List<Module> {
        return modules.filter { it.category == category }
    }

    fun getModuleByCommand(commandName: String): Module? {
        return modules.firstOrNull { it.commandName.equals(commandName, ignoreCase = true) }
    }

    fun getModuleByName(name: String): Module? {
        return modules.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    fun onClientPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        for (module in modules) {
            if (module.isEnabled) {
                val signal = module.onClientPacket(packet, session)
                if (signal == PacketSignal.HANDLED) {
                    return PacketSignal.HANDLED
                }
            }
        }
        return PacketSignal.UNHANDLED
    }

    fun onServerPacket(packet: BedrockPacket, session: ProxySession): PacketSignal {
        for (module in modules) {
            if (module.isEnabled) {
                val signal = module.onServerPacket(packet, session)
                if (signal == PacketSignal.HANDLED) {
                    return PacketSignal.HANDLED
                }
            }
        }
        return PacketSignal.UNHANDLED
    }

    fun onTick(session: ProxySession) {
        for (module in modules) {
            if (module.isEnabled) {
                module.onTick(session)
            }
        }
    }

    fun syncWithConfig() {
        val config = configManager.currentConfig
        for (module in modules) {
            val isEnabled = config.moduleStates[module.name] ?: false
            if (isEnabled) {
                module.enable()
            } else {
                module.disable()
            }
        }
    }

    fun saveToConfig() {
        val config = configManager.currentConfig
        for (module in modules) {
            config.moduleStates[module.name] = module.isEnabled
        }
    }
}
