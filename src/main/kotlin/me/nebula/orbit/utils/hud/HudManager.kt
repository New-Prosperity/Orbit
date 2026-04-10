package me.nebula.orbit.utils.hud

import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PlayerHud(
    val player: Player,
    val layout: HudLayout,
) {
    val values = ConcurrentHashMap<String, Any>()
    val groupItems = ConcurrentHashMap<String, MutableList<String>>()
    val conditions = ConcurrentHashMap<String, (Player) -> Boolean>()
    val bossBar: BossBar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
    var lastRendered: Component = Component.empty()
    var animationTick = 0L

    fun isElementVisible(elementId: String): Boolean =
        conditions[elementId]?.invoke(player) != false
}

object HudManager {

    private val layouts = ConcurrentHashMap<String, HudLayout>()
    private val playerLayers = ConcurrentHashMap<UUID, ConcurrentHashMap<String, PlayerHud>>()

    fun register(layout: HudLayout) {
        layouts[layout.id] = layout
    }

    fun show(player: Player, layoutId: String) {
        val layout = layouts[layoutId] ?: error("Unknown HUD layout: $layoutId")
        val layers = playerLayers.getOrPut(player.uuid) { ConcurrentHashMap() }
        layers[layoutId]?.let { existing ->
            player.hideBossBar(existing.bossBar)
        }
        val hud = PlayerHud(player, layout)
        layers[layoutId] = hud
        player.showBossBar(hud.bossBar)
        renderAndUpdate(hud)
    }

    fun hide(player: Player, layoutId: String) {
        val layers = playerLayers[player.uuid] ?: return
        val hud = layers.remove(layoutId) ?: return
        player.hideBossBar(hud.bossBar)
        if (layers.isEmpty()) playerLayers.remove(player.uuid)
    }

    fun hideAll(player: Player) {
        val layers = playerLayers.remove(player.uuid) ?: return
        layers.values.forEach { player.hideBossBar(it.bossBar) }
    }

    fun isShowing(player: Player, layoutId: String): Boolean =
        playerLayers[player.uuid]?.containsKey(layoutId) == true

    fun update(player: Player, elementId: String, value: Any) {
        val layers = playerLayers[player.uuid] ?: return
        for (hud in layers.values) {
            if (hud.layout.elements.any { it.id == elementId }) {
                hud.values[elementId] = value
                return
            }
        }
    }

    fun update(player: Player, layoutId: String, elementId: String, value: Any) {
        playerLayers[player.uuid]?.get(layoutId)?.values?.put(elementId, value)
    }

    fun addToGroup(player: Player, groupId: String, spriteId: String) {
        val layers = playerLayers[player.uuid] ?: return
        for (hud in layers.values) {
            if (hud.layout.elements.any { it.id == groupId }) {
                hud.groupItems.getOrPut(groupId) { mutableListOf() }.add(spriteId)
                return
            }
        }
    }

    fun removeFromGroup(player: Player, groupId: String, spriteId: String) {
        val layers = playerLayers[player.uuid] ?: return
        for (hud in layers.values) {
            hud.groupItems[groupId]?.remove(spriteId)
        }
    }

    fun playerHud(uuid: UUID, layoutId: String): PlayerHud? =
        playerLayers[uuid]?.get(layoutId)

    fun playerHuds(uuid: UUID): Collection<PlayerHud> =
        playerLayers[uuid]?.values ?: emptyList()

    fun activeLayoutIds(uuid: UUID): Set<String> =
        playerLayers[uuid]?.keys?.toSet() ?: emptySet()

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            val layers = playerLayers.remove(event.player.uuid) ?: return@addListener
            layers.values.forEach { event.player.hideBossBar(it.bossBar) }
        }
    }

    fun uninstall() {
        playerLayers.clear()
    }

    fun tick() {
        for (layers in playerLayers.values) {
            for (hud in layers.values) {
                hud.animationTick++
                renderAndUpdate(hud)
            }
        }
    }

    private fun renderAndUpdate(hud: PlayerHud) {
        val component = HudRenderer.render(hud)
        if (hud.lastRendered != component) {
            hud.lastRendered = component
            hud.bossBar.name(component)
        }
    }
}
