package me.nebula.orbit.utils.replay

import me.nebula.ether.utils.hazelcast.DistributedMap
import me.nebula.ether.utils.hazelcast.HazelcastStructureProvider
import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.Orbit
import java.io.Serializable

object PendingReplayFlushes {

    private val log = logger("PendingReplayFlushes")
    private const val STALE_THRESHOLD_MS = 10 * 60_000L

    private val pending: DistributedMap<String, Pending> by lazy {
        HazelcastStructureProvider.map("pending-replay-flushes")
    }

    data class Pending(
        val replayName: String,
        val matchId: String,
        val serverName: String,
        val startedAt: Long,
    ) : Serializable

    fun mark(replayName: String, matchId: String) {
        runCatching {
            pending[replayName] = Pending(replayName, matchId, Orbit.serverName, System.currentTimeMillis())
        }.onFailure { log.warn(it) { "Failed to mark pending replay flush: $replayName" } }
    }

    fun complete(replayName: String) {
        runCatching { pending.remove(replayName) }
            .onFailure { log.warn(it) { "Failed to clear pending replay flush: $replayName" } }
    }

    fun sweepStale() {
        val now = System.currentTimeMillis()
        val toRemove = mutableListOf<String>()
        for ((name, entry) in pending) {
            if (entry.serverName != Orbit.serverName) continue
            if (now - entry.startedAt > STALE_THRESHOLD_MS) toRemove += name
        }
        for (name in toRemove) {
            log.warn { "Pending replay flush stale on startup, dropping: $name (matchId=${pending[name]?.matchId})" }
            pending.remove(name)
        }
    }
}
