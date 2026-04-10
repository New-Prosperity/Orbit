package me.nebula.orbit

import me.nebula.ether.utils.module.Module
import me.nebula.orbit.cosmetic.CosmeticContext
import me.nebula.orbit.cosmetic.CosmeticListener
import me.nebula.orbit.cosmetic.installCosmeticInteraction
import me.nebula.orbit.marketplace.MarketplaceExpiry
import me.nebula.orbit.nick.NickManager
import me.nebula.orbit.progression.ProgressionSubscribers
import me.nebula.orbit.staff.StaffSpectateManager
import me.nebula.orbit.utils.actionbar.ActionBarManager
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.bossbar.AnimatedBossBarManager
import me.nebula.orbit.utils.botai.BotAI
import me.nebula.orbit.utils.counter.AnimatedCounterManager
import me.nebula.orbit.utils.drain.ServerDrainManager
import me.nebula.orbit.utils.hud.HudManager
import me.nebula.orbit.utils.metrics.MetricsPublisher
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.tablist.TabListManager
import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.MinecraftServer

object ModelEngineModule : Module("model-engine") {
    override fun onEnable() {
        ModelEngine.install()
    }

    override fun onDisable() {
        ModelEngine.uninstall()
    }
}

object BotAIModule : Module("bot-ai") {
    override fun onEnable() {
        BotAI.install()
    }

    override fun onDisable() {
        BotAI.uninstall()
    }
}

object HudSystemModule : Module("hud-system") {
    private var eventNode: net.minestom.server.event.EventNode<*>? = null

    override fun onEnable() {
        val node = net.minestom.server.event.EventNode.all("hud-system")
        HudManager.install(node)
        ActionBarManager.install(node)
        AnimatedCounterManager.install(node)
        AnimatedBossBarManager.install(node)
        TabListManager.install(node)
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    override fun onDisable() {
        AnimatedCounterManager.uninstall()
        AnimatedBossBarManager.uninstall()
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
    }
}

object CosmeticSystemModule : Module("cosmetic-system") {
    override fun onEnable() {
        check(Orbit.isModeInitialized) { "cosmetic-system requires Orbit.mode to be initialized" }
        val handler = MinecraftServer.getGlobalEventHandler()
        CosmeticListener.activeConfig = Orbit.mode.cosmeticConfig
        val context = CosmeticContext(CosmeticListener)
        Orbit.cosmetics = context
        CosmeticListener.context = context
        CosmeticListener.install(handler)
        installCosmeticInteraction(handler)
        context.install()
    }

    override fun onDisable() {
        CosmeticListener.uninstall()
        Orbit.cosmetics.uninstall()
    }
}

object StaffSystemModule : Module("staff-system") {
    override fun onEnable() {
        NickManager.installListeners()
        VanishManager.installListeners()
        StaffSpectateManager.installListeners()
    }

    override fun onDisable() {
        NickManager.uninstallListeners()
        VanishManager.uninstallListeners()
        StaffSpectateManager.uninstallListeners()
    }
}

object AntiCheatModule : Module("anti-cheat") {
    override fun onEnable() {
        AntiCheat.install(MinecraftServer.getGlobalEventHandler())
    }

    override fun onDisable() {
        AntiCheat.uninstall()
    }
}

object ObservabilityModule : Module("observability") {
    override fun onEnable() {
        MetricsPublisher.initialize()
        ServerDrainManager.install()
    }

    override fun onDisable() {
        ServerDrainManager.uninstall()
        MetricsPublisher.shutdown()
    }
}

object ProgressionModule : Module("progression") {
    override fun onEnable() {
        ProgressionSubscribers.install()
    }

    override fun onDisable() {
        ProgressionSubscribers.uninstall()
    }
}

object MarketplaceModule : Module("marketplace") {
    override fun onEnable() {
        MarketplaceExpiry.install()
    }

    override fun onDisable() {
        MarketplaceExpiry.uninstall()
    }
}
