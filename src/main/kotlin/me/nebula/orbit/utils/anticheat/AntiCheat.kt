package me.nebula.orbit.utils.anticheat

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.rank.RankManager
import me.nebula.orbit.utils.anticheat.checks.CombatCheck
import me.nebula.orbit.utils.anticheat.checks.MovementCheck
import me.nebula.orbit.utils.chat.mm
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object AntiCheat {

    private val logger = logger("AntiCheat")
    private val trackers = ConcurrentHashMap<UUID, ViolationTracker>()
    private var eventNode: EventNode<Event>? = null

    const val MOVEMENT_KICK_THRESHOLD = 20
    const val COMBAT_KICK_THRESHOLD = 15
    const val BYPASS_PERMISSION = "orbit.anticheat.bypass"

    fun tracker(uuid: UUID): ViolationTracker =
        trackers.computeIfAbsent(uuid) { ViolationTracker() }

    fun flag(uuid: UUID, type: String, weight: Int, threshold: Int) {
        if (RankManager.hasPermission(uuid, BYPASS_PERMISSION)) return
        val tracker = tracker(uuid)
        tracker.addViolation(type, weight)
        logger.debug { "Flag: $uuid | $type (weight=$weight, total=${tracker.totalViolations()})" }
        if (tracker.shouldKick(threshold)) {
            val player = MinecraftServer.getConnectionManager().onlinePlayers
                .firstOrNull { it.uuid == uuid } ?: return
            trackers.remove(uuid)
            player.kick(mm("<red>Disconnected"))
            logger.info { "Kicked ${player.username} (${player.uuid}) for exceeding $type threshold" }
        }
    }

    fun install(eventNode: EventNode<Event>) {
        val node = EventNode.all("anticheat")

        MovementCheck.install(node)
        CombatCheck.install(node)

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            val uuid = event.player.uuid
            trackers.remove(uuid)
            MovementCheck.cleanup(uuid)
            CombatCheck.cleanup(uuid)
        }

        eventNode.addChild(node)
        this.eventNode = node
        logger.info { "AntiCheat installed" }
    }

    fun uninstall() {
        val node = eventNode ?: return
        node.parent?.removeChild(node)
        eventNode = null
        trackers.clear()
        MovementCheck.clearAll()
        CombatCheck.clearAll()
        logger.info { "AntiCheat uninstalled" }
    }
}
