package me.nebula.orbit.utils.drain

import me.nebula.gravity.server.LiveServer
import me.nebula.gravity.server.LiveServerRegistry
import me.nebula.gravity.server.ServerType
import me.nebula.orbit.Orbit

object HubSelector {

    fun selectFallback(): LiveServer? {
        val self = Orbit.serverName
        val hub = LiveServerRegistry.byType(ServerType.GAME)
            .filter { it.name != self && !it.drain && it.gameMode == null }
            .filter { it.maxPlayers <= 0 || it.playerCount < it.maxPlayers }
            .minByOrNull { it.playerCount }
        if (hub != null) return hub

        return LiveServerRegistry.byType(ServerType.LIMBO)
            .filter { it.name != self && !it.drain }
            .minByOrNull { it.playerCount }
    }
}
