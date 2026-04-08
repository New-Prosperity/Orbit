package me.nebula.orbit.utils.drain

import me.nebula.gravity.server.ServerData
import me.nebula.gravity.server.ServerStore
import me.nebula.gravity.server.activeHubPredicate
import me.nebula.gravity.server.activeLimboPredicate
import me.nebula.orbit.Orbit

object HubSelector {

    fun selectFallback(): ServerData? {
        val hub = runCatching {
            ServerStore.entries(activeHubPredicate())
                .asSequence()
                .filter { it.value.name != Orbit.serverName }
                .minByOrNull { it.value.playerCount }
                ?.value
        }.getOrNull()
        if (hub != null) return hub

        return runCatching {
            ServerStore.entries(activeLimboPredicate())
                .asSequence()
                .filter { it.value.name != Orbit.serverName }
                .firstOrNull()
                ?.value
        }.getOrNull()
    }
}
