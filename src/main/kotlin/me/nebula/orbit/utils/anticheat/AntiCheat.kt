package me.nebula.orbit.utils.anticheat

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.anticheat.FlagPlayerProcessor
import me.nebula.gravity.anticheat.FlaggedPlayerStore
import me.nebula.gravity.anticheat.PlayerFlag
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.PlayerFlaggedMessage
import me.nebula.gravity.cache.CacheSlots
import me.nebula.gravity.cache.PlayerCache
import me.nebula.gravity.property.NetworkProperties
import me.nebula.gravity.property.PropertyStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.translation.translate
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
    private val flaggedLocally = ConcurrentHashMap.newKeySet<UUID>()
    private var eventNode: EventNode<Event>? = null

    val movementFlagThreshold: Int get() = PropertyStore[NetworkProperties.AC_MOVEMENT_FLAG_THRESHOLD]
    val movementKickThreshold: Int get() = PropertyStore[NetworkProperties.AC_MOVEMENT_KICK_THRESHOLD]
    val combatFlagThreshold: Int get() = PropertyStore[NetworkProperties.AC_COMBAT_FLAG_THRESHOLD]
    val combatKickThreshold: Int get() = PropertyStore[NetworkProperties.AC_COMBAT_KICK_THRESHOLD]

    const val BYPASS_PERMISSION = "orbit.anticheat.bypass"
    private const val STAFF_ALERT_PERMISSION = "staff.inspect"

    fun tracker(uuid: UUID): ViolationTracker =
        trackers.computeIfAbsent(uuid) { ViolationTracker() }

    fun flag(uuid: UUID, type: String, weight: Int, flagThreshold: Int, kickThreshold: Int) {
        if (hasCachedPerm(uuid, BYPASS_PERMISSION)) return
        val tracker = tracker(uuid)
        tracker.addViolation(type, weight)
        val total = tracker.totalViolations()
        logger.debug { "Flag: $uuid | $type (weight=$weight, total=$total)" }

        if (total >= kickThreshold) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid) ?: return
            trackers.remove(uuid)
            flaggedLocally.remove(uuid)
            player.kick(mm("<red>Disconnected"))
            logger.info { "Kicked ${player.username} (${player.uuid}) for exceeding $type kick threshold ($total/$kickThreshold)" }
            return
        }

        if (total >= flagThreshold && flaggedLocally.add(uuid)) {
            val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
            val playerName = player?.username ?: uuid.toString()

            val playerFlag = PlayerFlag(
                checkType = type,
                serverName = Orbit.serverName,
                gameMode = Orbit.gameMode,
                violations = total,
                timestamp = System.currentTimeMillis(),
            )
            FlaggedPlayerStore.executeOnKey(uuid, FlagPlayerProcessor(playerFlag))

            NetworkMessenger.publish(PlayerFlaggedMessage(
                playerId = uuid,
                playerName = playerName,
                checkType = type,
                violations = total,
                serverName = Orbit.serverName,
                gameMode = Orbit.gameMode,
            ))

            alertStaff(playerName, type, total)
            logger.info { "Flagged $playerName ($uuid) for $type ($total violations)" }
        }
    }

    fun isFlagged(uuid: UUID): Boolean = FlaggedPlayerStore.exists(uuid)

    private fun hasCachedPerm(uuid: UUID, permission: String): Boolean {
        val perms = PlayerCache.get(uuid)?.get(CacheSlots.PERMISSIONS) ?: return false
        return "*" in perms || permission in perms
    }

    private fun alertStaff(playerName: String, checkType: String, violations: Int) {
        for (player in MinecraftServer.getConnectionManager().onlinePlayers) {
            if (hasCachedPerm(player.uuid, STAFF_ALERT_PERMISSION)) {
                player.sendMessage(player.translate("orbit.anticheat.staff_alert",
                    "player" to playerName,
                    "check" to checkType,
                    "violations" to violations.toString()
                ))
            }
        }
    }

    fun install(eventNode: EventNode<Event>) {
        if (AntiCheatRegistry.all().isEmpty()) {
            AntiCheatRegistry.register(MovementCheck)
            AntiCheatRegistry.register(CombatCheck)
        }

        val node = EventNode.all("anticheat")
        for (check in AntiCheatRegistry.all()) check.install(node)

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            val uuid = event.player.uuid
            trackers.remove(uuid)
            flaggedLocally.remove(uuid)
            for (check in AntiCheatRegistry.all()) check.cleanup(uuid)
        }

        eventNode.addChild(node)
        this.eventNode = node
        logger.info { "AntiCheat installed (${AntiCheatRegistry.all().size} checks)" }
    }

    fun uninstall() {
        val node = eventNode ?: return
        node.parent?.removeChild(node)
        eventNode = null
        trackers.clear()
        flaggedLocally.clear()
        for (check in AntiCheatRegistry.all()) check.clearAll()
        logger.info { "AntiCheat uninstalled" }
    }
}
