package me.nebula.orbit.utils.resourcepack

import net.kyori.adventure.resource.ResourcePackInfo
import net.kyori.adventure.resource.ResourcePackRequest
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class PackConfig(
    val url: String,
    val hash: String = "",
    val required: Boolean = false,
    val prompt: Component? = null,
)

object ResourcePackManager {

    private val packs = ConcurrentHashMap<String, PackConfig>()
    private val eventNode = EventNode.all("resource-pack-manager")
    private var installed = false
    private var autoSend = false

    fun register(name: String, config: PackConfig) {
        packs[name] = config
    }

    fun unregister(name: String) = packs.remove(name)

    fun send(player: Player, name: String) {
        val config = packs[name] ?: return

        val info = ResourcePackInfo.resourcePackInfo()
            .uri(URI.create(config.url))
            .hash(config.hash)
            .build()

        val request = ResourcePackRequest.resourcePackRequest()
            .packs(info)
            .required(config.required)
            .apply { if (config.prompt != null) prompt(config.prompt) }
            .build()

        player.sendResourcePacks(request)
    }

    fun sendAll(player: Player) {
        packs.keys.forEach { send(player, it) }
    }

    fun enableAutoSend() {
        autoSend = true
        ensureInstalled()
    }

    fun install() {
        if (installed) return

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (autoSend && event.isFirstSpawn) {
                sendAll(event.player)
            }
        }

        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
        installed = true
    }

    fun uninstall() {
        if (!installed) return
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        installed = false
    }

    fun clear() {
        packs.clear()
        uninstall()
    }

    private fun ensureInstalled() {
        if (!installed) install()
    }
}
