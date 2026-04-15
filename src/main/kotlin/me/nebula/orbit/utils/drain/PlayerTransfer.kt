package me.nebula.orbit.utils.drain

import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.messaging.NetworkMessenger
import me.nebula.gravity.messaging.TransferPlayerMessage
import me.nebula.gravity.server.ServerData
import me.nebula.orbit.Orbit
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.UUID

object PlayerTransfer {

    private val logger = logger("PlayerTransfer")

    fun transferToHub(player: Player, target: ServerData? = null): Boolean {
        val resolved = target ?: HubSelector.selectFallback() ?: run {
            logger.warn { "No hub or limbo available to transfer ${player.username}" }
            return false
        }
        despawnCosmetics(player)
        return publishTransfer(player.uuid, resolved.name)
    }

    fun transferToHubByUuid(uuid: UUID, target: ServerData? = null): Boolean {
        val resolved = target ?: HubSelector.selectFallback() ?: run {
            logger.warn { "No hub or limbo available to transfer $uuid" }
            return false
        }
        val player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
        if (player != null) despawnCosmetics(player)
        return publishTransfer(uuid, resolved.name)
    }

    fun transferAllToHub(): Int {
        val target = HubSelector.selectFallback() ?: run {
            logger.warn { "No hub or limbo available — cannot transfer players" }
            return 0
        }
        val players = MinecraftServer.getConnectionManager().onlinePlayers.toList()
        var sent = 0
        for (player in players) {
            if (transferToHub(player, target)) sent++
        }
        if (sent > 0) logger.info { "Dispatched $sent transfer(s) to ${target.name}" }
        return sent
    }

    private fun despawnCosmetics(player: Player) {
        runCatching { Orbit.cosmetics.despawnAll(player) } // noqa: dangling runCatching
    }

    private fun publishTransfer(uuid: UUID, serverName: String): Boolean =
        runCatching { NetworkMessenger.publish(TransferPlayerMessage(uuid, serverName)) }.onFailure { logger.warn(it) { "Failed to publish transfer for $uuid" } }.isSuccess
}
