package me.nebula.orbit.mode.game

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface PlayerState {
    data object Alive : PlayerState
    data object Spectating : PlayerState
    data class Disconnected(val since: Long = System.currentTimeMillis()) : PlayerState
}

class PlayerTracker {

    private val players = ConcurrentHashMap<UUID, PlayerState>()

    val alive: Set<UUID> get() = players.entries.filter { it.value is PlayerState.Alive }.map { it.key }.toSet()
    val spectating: Set<UUID> get() = players.entries.filter { it.value is PlayerState.Spectating }.map { it.key }.toSet()
    val disconnected: Set<UUID> get() = players.entries.filter { it.value is PlayerState.Disconnected }.map { it.key }.toSet()
    val aliveCount: Int get() = players.values.count { it is PlayerState.Alive }
    val size: Int get() = players.size

    fun join(uuid: UUID) {
        players[uuid] = PlayerState.Alive
    }

    fun eliminate(uuid: UUID) {
        players[uuid] = PlayerState.Spectating
    }

    fun disconnect(uuid: UUID) {
        val current = players[uuid] ?: return
        if (current is PlayerState.Alive) {
            players[uuid] = PlayerState.Disconnected()
        }
    }

    fun reconnect(uuid: UUID) {
        val current = players[uuid] ?: return
        when (current) {
            is PlayerState.Disconnected -> players[uuid] = PlayerState.Alive
            else -> {}
        }
    }

    fun remove(uuid: UUID) {
        players.remove(uuid)
    }

    fun stateOf(uuid: UUID): PlayerState? = players[uuid]

    fun isAlive(uuid: UUID): Boolean = players[uuid] is PlayerState.Alive

    fun isSpectating(uuid: UUID): Boolean = players[uuid] is PlayerState.Spectating

    fun isDisconnected(uuid: UUID): Boolean = players[uuid] is PlayerState.Disconnected

    operator fun contains(uuid: UUID): Boolean = players.containsKey(uuid)

    fun clear() {
        players.clear()
    }
}
