package me.nebula.orbit.server

import me.nebula.ether.utils.hazelcast.HazelcastHealth
import me.nebula.ether.utils.logging.logger
import me.nebula.gravity.server.LiveServer
import me.nebula.gravity.server.LiveServerRegistry
import net.minestom.server.MinecraftServer

object OrbitHeartbeat {

    private val log = logger("OrbitHeartbeat")
    @Volatile private var selfState: LiveServer? = null

    val isDraining: Boolean get() = selfState?.drain == true

    fun register(server: LiveServer) {
        selfState = server
        LiveServerRegistry.register(server)
        HazelcastHealth.addListener(::onHazelcastState)
    }

    fun heartbeat() {
        updateSelf { it.copy(playerCount = MinecraftServer.getConnectionManager().onlinePlayers.size) }
    }

    fun updateSelf(updater: (LiveServer) -> LiveServer) {
        val current = selfState ?: return
        val next = updater(current)
        selfState = next
        runCatching { LiveServerRegistry.put(next) }
            .onFailure { log.warn(it) { "updateSelf put failed" } }
    }

    fun deregister() {
        val state = selfState ?: return
        runCatching { LiveServerRegistry.deregister(state.name) }
            .onFailure { log.warn(it) { "Failed to deregister '${state.name}'" } }
    }

    private fun onHazelcastState(connected: Boolean) {
        if (!connected) return
        val state = selfState ?: return
        log.info { "Hazelcast reconnected — re-registering '${state.name}'" }
        runCatching { LiveServerRegistry.put(state) }
            .onFailure { log.warn(it) { "Re-register after reconnect failed" } }
    }
}
