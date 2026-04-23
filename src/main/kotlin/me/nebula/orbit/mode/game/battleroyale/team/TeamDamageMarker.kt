package me.nebula.orbit.mode.game.battleroyale.team

import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.mode.game.battleroyale.BattleRoyaleTeamConfig
import me.nebula.orbit.utils.entityglow.EntityGlowManager
import me.nebula.orbit.utils.scheduler.delay
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TeamDamageMarker(
    private val tracker: PlayerTracker,
    private val config: BattleRoyaleTeamConfig,
    private val durationTicks: Int = 60,
) {

    private val pendingUnset = ConcurrentHashMap<UUID, Task>()

    fun flash(victim: Player) {
        if (!config.enabled || config.teamSize <= 1) return
        val team = tracker.teamOf(victim.uuid) ?: return
        val connection = MinecraftServer.getConnectionManager()
        for (viewerUuid in tracker.aliveInTeam(team)) {
            if (viewerUuid == victim.uuid) continue
            val viewer = connection.getOnlinePlayerByUuid(viewerUuid) ?: continue
            EntityGlowManager.setGlowing(viewer, victim, true)
        }
        pendingUnset.remove(victim.uuid)?.cancel()
        pendingUnset[victim.uuid] = delay(durationTicks) { clearFor(victim) }
    }

    fun clearFor(victim: Player) {
        pendingUnset.remove(victim.uuid)?.cancel()
        val team = tracker.teamOf(victim.uuid) ?: return
        val connection = MinecraftServer.getConnectionManager()
        for (viewerUuid in tracker.teamMembers(team)) {
            if (viewerUuid == victim.uuid) continue
            val viewer = connection.getOnlinePlayerByUuid(viewerUuid) ?: continue
            EntityGlowManager.setGlowing(viewer, victim, false)
        }
    }

    fun clear() {
        pendingUnset.values.forEach { it.cancel() }
        pendingUnset.clear()
    }
}
