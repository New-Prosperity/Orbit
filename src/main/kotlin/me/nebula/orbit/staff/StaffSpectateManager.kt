package me.nebula.orbit.staff

import me.nebula.ether.utils.hazelcast.HazelcastStructureProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.StaffSpectateFollowMessage
import me.nebula.gravity.session.SessionStore
import me.nebula.orbit.Orbit
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.cinematic.CinematicCamera
import me.nebula.orbit.utils.scheduler.delay
import me.nebula.orbit.utils.vanish.VanishManager
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import java.util.UUID
import me.nebula.gravity.translation.Keys

object StaffSpectateManager {

    private val spectatingMap by lazy { HazelcastStructureProvider.replicatedMap<UUID, UUID>("staff-spectating") }
    private val preSpectateVanishMap by lazy { HazelcastStructureProvider.replicatedMap<UUID, Boolean>("pre-spectate-vanish") }
    private val logger = logger("StaffSpectate")
    private var eventNode: EventNode<Event>? = null

    fun spectate(staff: Player, targetUuid: UUID): Boolean {
        if (targetUuid == staff.uuid) return false

        val resolvedTarget = resolveEndTarget(targetUuid)

        CinematicCamera.stop(staff)

        preSpectateVanishMap[staff.uuid] = VanishManager.isVanished(staff)
        if (!VanishManager.isVanished(staff)) VanishManager.vanish(staff)
        spectatingMap[staff.uuid] = resolvedTarget

        val targetSession = SessionStore.load(resolvedTarget)
        if (targetSession == null) {
            logger.info { "Spectate: ${staff.username} -> $resolvedTarget (offline, waiting)" }
            return true
        }

        if (targetSession.serverName == Orbit.serverName) {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(resolvedTarget)
            if (target != null) attachCamera(staff, target)
        } else {
            NetworkMessenger.publish(StaffSpectateFollowMessage(staff.uuid, resolvedTarget, targetSession.serverName))
        }

        logger.info { "Spectate: ${staff.username} -> $resolvedTarget" }
        return true
    }

    fun unspectate(staff: Player) {
        spectatingMap.remove(staff.uuid)
        staff.stopSpectating()
        staff.gameMode = GameMode.ADVENTURE
        if (preSpectateVanishMap.remove(staff.uuid) != true) VanishManager.unvanish(staff)
        staff.teleport(Orbit.mode.activeSpawnPoint)
        logger.info { "Unspectate: ${staff.username}" }
    }

    fun switchTarget(staff: Player, newTargetUuid: UUID): Boolean {
        if (newTargetUuid == staff.uuid) return false

        val resolvedTarget = resolveEndTarget(newTargetUuid)

        staff.stopSpectating()
        spectatingMap[staff.uuid] = resolvedTarget

        val targetSession = SessionStore.load(resolvedTarget)
        if (targetSession == null) {
            staff.sendMessage(staff.translate(Keys.Orbit.Spectate.TargetOffline))
            logger.info { "SwitchTarget: ${staff.username} -> $resolvedTarget (offline, waiting)" }
            return true
        }

        if (targetSession.serverName == Orbit.serverName) {
            val target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(resolvedTarget)
            if (target != null) attachCamera(staff, target)
        } else {
            NetworkMessenger.publish(StaffSpectateFollowMessage(staff.uuid, resolvedTarget, targetSession.serverName))
        }

        logger.info { "SwitchTarget: ${staff.username} -> $resolvedTarget" }
        return true
    }

    fun isSpectating(player: Player): Boolean = spectatingMap.containsKey(player.uuid)

    fun getTarget(player: Player): UUID? = spectatingMap[player.uuid]

    fun attachCamera(staff: Player, target: Player) {
        staff.gameMode = GameMode.SPECTATOR
        staff.teleport(target.position)
        staff.spectate(target)
    }

    fun installListeners() {
        val node = EventNode.all("staff-spectate")

        node.addListener(PlayerSpawnEvent::class.java) { event ->
            val player = event.player

            val targetUuid = spectatingMap[player.uuid]
            if (targetUuid != null) {
                delay(5) {
                    if (spectatingMap[player.uuid] != targetUuid) return@delay
                    val target = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(targetUuid)
                    if (target != null && target.instance == player.instance) {
                        attachCamera(player, target)
                    } else {
                        player.gameMode = GameMode.SPECTATOR
                    }
                }
            }

            val staffWatching = spectatingMap.entries.filter { it.value == player.uuid }.map { it.key }
            if (staffWatching.isNotEmpty()) {
                delay(5) {
                    for (staffUuid in staffWatching) {
                        if (spectatingMap[staffUuid] != player.uuid) continue
                        val staff = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(staffUuid) ?: continue
                        if (staff.instance == player.instance) {
                            attachCamera(staff, player)
                        }
                    }
                }
            }
        }

        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            val player = event.player

            if (spectatingMap.containsKey(player.uuid)) {
                spectatingMap.remove(player.uuid)
                preSpectateVanishMap.remove(player.uuid)
            }

            val staffWatching = spectatingMap.entries.filter { it.value == player.uuid }.map { it.key }
            for (staffUuid in staffWatching) {
                val staff = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(staffUuid) ?: continue
                staff.stopSpectating()
                staff.sendMessage(staff.translate(Keys.Orbit.Spectate.TargetOffline))
            }
        }

        SessionStore.listen {
            onUpdated { uuid, newSession ->
                val staffIds = spectatingMap.entries.filter { it.value == uuid }.map { it.key }
                for (staffId in staffIds) {
                    val staffSession = SessionStore.load(staffId) ?: continue
                    if (staffSession.serverName == newSession.serverName) continue
                    val staffOnThisServer = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(staffId)
                    if (staffOnThisServer != null) {
                        NetworkMessenger.publish(StaffSpectateFollowMessage(staffId, uuid, newSession.serverName))
                    }
                }
            }
        }

        eventNode = node
        MinecraftServer.getGlobalEventHandler().addChild(node)
    }

    fun uninstallListeners() {
        val node = eventNode ?: return
        MinecraftServer.getGlobalEventHandler().removeChild(node)
        eventNode = null
    }

    private fun resolveEndTarget(targetUuid: UUID): UUID {
        var current = targetUuid
        val visited = mutableSetOf<UUID>()
        while (true) {
            if (!visited.add(current)) return current
            val next = spectatingMap[current] ?: return current
            current = next
        }
    }
}
