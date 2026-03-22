package me.nebula.orbit.utils.actionbar

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ActionBarSlot(
    val id: String,
    val priority: Int,
    val content: Component,
    val expiresAt: Long,
    val visibleWhen: ((Player) -> Boolean)?,
)

class PlayerActionBarState {
    val slots = ConcurrentHashMap<String, ActionBarSlot>()
    var lastComposed: Component = Component.empty()
    var ticksSinceLastSend: Int = 0
}

object ActionBarManager {

    var separator: Component = Component.text(" \u2502 ", NamedTextColor.DARK_GRAY)
    private const val RESEND_INTERVAL = 40

    private val miniMessage = MiniMessage.miniMessage()
    private val state = ConcurrentHashMap<UUID, PlayerActionBarState>()

    fun set(
        player: Player,
        id: String,
        priority: Int,
        content: Component,
        durationMs: Long = 0,
        visibleWhen: ((Player) -> Boolean)? = null,
    ) {
        val ps = state.getOrPut(player.uuid) { PlayerActionBarState() }
        val expiresAt = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
        ps.slots[id] = ActionBarSlot(id, priority, content, expiresAt, visibleWhen)
        composeAndSend(player, ps)
    }

    fun set(
        player: Player,
        id: String,
        priority: Int,
        message: String,
        durationMs: Long = 0,
        visibleWhen: ((Player) -> Boolean)? = null,
    ) {
        set(player, id, priority, miniMessage.deserialize(message), durationMs, visibleWhen)
    }

    fun update(player: Player, id: String, content: Component) {
        val ps = state[player.uuid] ?: return
        val existing = ps.slots[id] ?: return
        ps.slots[id] = ActionBarSlot(id, existing.priority, content, existing.expiresAt, existing.visibleWhen)
        composeAndSend(player, ps)
    }

    fun update(player: Player, id: String, message: String) {
        update(player, id, miniMessage.deserialize(message))
    }

    fun remove(player: Player, id: String) {
        val ps = state[player.uuid] ?: return
        ps.slots.remove(id)
        if (ps.slots.isEmpty()) {
            state.remove(player.uuid)
            player.sendActionBar(Component.empty())
        } else {
            composeAndSend(player, ps)
        }
    }

    fun clear(player: Player) {
        state.remove(player.uuid)
        player.sendActionBar(Component.empty())
    }

    fun has(player: Player, id: String): Boolean =
        state[player.uuid]?.slots?.containsKey(id) == true

    fun activeSlots(player: Player): Set<String> =
        state[player.uuid]?.slots?.keys?.toSet() ?: emptySet()

    fun slot(player: Player, id: String): ActionBarSlot? =
        state[player.uuid]?.slots?.get(id)

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerDisconnectEvent::class.java) { event ->
            state.remove(event.player.uuid)
        }
    }

    fun tick() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<UUID>()

        for ((uuid, ps) in state) {
            ps.slots.entries.removeIf { it.value.expiresAt > 0 && now >= it.value.expiresAt }

            if (ps.slots.isEmpty()) {
                toRemove += uuid
                continue
            }

            ps.ticksSinceLastSend++
            if (ps.ticksSinceLastSend >= RESEND_INTERVAL) {
                val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
                if (player != null) {
                    composeAndSend(player, ps)
                } else {
                    toRemove += uuid
                }
            }
        }

        for (uuid in toRemove) {
            val ps = state.remove(uuid) ?: continue
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            if (player != null && ps.slots.isEmpty()) {
                player.sendActionBar(Component.empty())
            }
        }
    }

    private fun composeAndSend(player: Player, ps: PlayerActionBarState) {
        val composed = compose(player, ps.slots.values)
        ps.lastComposed = composed
        ps.ticksSinceLastSend = 0
        player.sendActionBar(composed)
    }

    private fun compose(player: Player, slots: Collection<ActionBarSlot>): Component {
        val visible = slots.filter { it.visibleWhen?.invoke(player) != false }
        val sorted = visible.sortedBy { it.priority }
        return Component.join(JoinConfiguration.separator(separator), sorted.map { it.content })
    }
}

fun Player.setActionBarSlot(
    id: String,
    priority: Int,
    content: Component,
    durationMs: Long = 0,
    visibleWhen: ((Player) -> Boolean)? = null,
) = ActionBarManager.set(this, id, priority, content, durationMs, visibleWhen)

fun Player.setActionBarSlot(
    id: String,
    priority: Int,
    message: String,
    durationMs: Long = 0,
    visibleWhen: ((Player) -> Boolean)? = null,
) = ActionBarManager.set(this, id, priority, message, durationMs, visibleWhen)

fun Player.updateActionBarSlot(id: String, content: Component) =
    ActionBarManager.update(this, id, content)

fun Player.updateActionBarSlot(id: String, message: String) =
    ActionBarManager.update(this, id, message)

fun Player.removeActionBarSlot(id: String) =
    ActionBarManager.remove(this, id)

fun Player.clearActionBar() =
    ActionBarManager.clear(this)

fun Player.hasActionBarSlot(id: String): Boolean =
    ActionBarManager.has(this, id)

val Player.activeActionBarSlots: Set<String>
    get() = ActionBarManager.activeSlots(this)
