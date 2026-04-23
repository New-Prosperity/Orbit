package me.nebula.orbit.mode.game.battleroyale.team

import me.nebula.gravity.translation.Keys
import me.nebula.orbit.displayUsername
import me.nebula.orbit.mode.game.PlayerTracker
import me.nebula.orbit.mode.game.battleroyale.BattleRoyaleTeamConfig
import me.nebula.orbit.mode.game.battleroyale.downed.DownedPlayerController
import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.bossbar.AnimatedBossBarManager
import net.kyori.adventure.bossbar.BossBar
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.atan2
import kotlin.math.sqrt

class TeamHudController(
    private val tracker: PlayerTracker,
    private val downed: DownedPlayerController,
    private val config: BattleRoyaleTeamConfig,
) {

    private val viewerBars = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    fun tick() {
        if (!config.enabled || config.teamSize <= 1) return
        val connection = MinecraftServer.getConnectionManager()
        for (viewerUuid in tracker.alive.toSet()) {
            val viewer = connection.getOnlinePlayerByUuid(viewerUuid) ?: continue
            val team = tracker.teamOf(viewerUuid) ?: continue
            val teammates = tracker.teamMembers(team).filter { it != viewerUuid }
            val shown = viewerBars.computeIfAbsent(viewerUuid) { ConcurrentHashMap.newKeySet() }

            val stillShown = mutableSetOf<UUID>()
            for (mateUuid in teammates) {
                renderSlot(viewer, mateUuid)
                stillShown += mateUuid
            }
            val stale = shown - stillShown
            for (gone in stale) {
                AnimatedBossBarManager.remove(viewer, barId(gone))
                shown.remove(gone)
            }
            shown += stillShown
        }
    }

    fun onPlayerRemoved(uuid: UUID) {
        viewerBars.remove(uuid)
        for (viewer in MinecraftServer.getConnectionManager().onlinePlayers) {
            AnimatedBossBarManager.remove(viewer, barId(uuid))
        }
    }

    fun clear() {
        val connection = MinecraftServer.getConnectionManager()
        for ((viewerUuid, shown) in viewerBars) {
            val viewer = connection.getOnlinePlayerByUuid(viewerUuid) ?: continue
            for (mate in shown) {
                AnimatedBossBarManager.remove(viewer, barId(mate))
            }
        }
        viewerBars.clear()
    }

    private fun renderSlot(viewer: Player, mateUuid: UUID) {
        val mate = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(mateUuid)
        val id = barId(mateUuid)
        val state = resolveState(viewer, mateUuid, mate)

        val titleComponent = when (state) {
            is SlotState.Alive -> viewer.translate(
                Keys.Orbit.Game.Br.TeamHud.Alive,
                "name" to state.name,
                "hp" to state.hp.toString(),
                "distance" to state.distance.toString(),
                "dir" to state.direction,
            )
            is SlotState.Downed -> viewer.translate(
                Keys.Orbit.Game.Br.TeamHud.Downed,
                "name" to state.name,
                "distance" to state.distance.toString(),
                "dir" to state.direction,
            )
            is SlotState.Dead -> viewer.translate(Keys.Orbit.Game.Br.TeamHud.Dead, "name" to state.name)
            is SlotState.Offline -> viewer.translate(Keys.Orbit.Game.Br.TeamHud.Offline, "name" to state.name)
        }

        val instance = AnimatedBossBarManager.get(viewer, id)
            ?: AnimatedBossBarManager.create(viewer, id, "", state.progress, state.color, BossBar.Overlay.PROGRESS)
        instance.updateTitle(titleComponent)
        instance.updateColor(state.color)
        instance.setProgressInstant(state.progress)
    }

    private fun resolveState(viewer: Player, mateUuid: UUID, mate: Player?): SlotState {
        val resolvedName = mate?.displayUsername ?: mateUuid.toString().take(8)
        if (mate == null) return SlotState.Offline(resolvedName)
        if (!tracker.isAlive(mateUuid) && !downed.isDowned(mateUuid)) return SlotState.Dead(resolvedName)
        val distance = horizontalDistance(viewer, mate)
        val direction = computeRelativeBearing(viewer.position.x(), viewer.position.z(), mate.position.x(), mate.position.z())
        if (downed.isDowned(mateUuid)) {
            return SlotState.Downed(resolvedName, distance, direction)
        }
        val maxHealth = mate.getAttributeValue(Attribute.MAX_HEALTH).toFloat().coerceAtLeast(1f)
        val progress = (mate.health / maxHealth).coerceIn(0f, 1f)
        return SlotState.Alive(resolvedName, mate.health.toInt(), distance, direction, progress)
    }

    private fun horizontalDistance(viewer: Player, mate: Player): Int {
        val dx = mate.position.x() - viewer.position.x()
        val dz = mate.position.z() - viewer.position.z()
        return sqrt(dx * dx + dz * dz).toInt()
    }

    private fun barId(uuid: UUID): String = "br-team-$uuid"

    private sealed interface SlotState {
        val progress: Float
        val color: BossBar.Color

        data class Alive(
            val name: String,
            val hp: Int,
            val distance: Int,
            val direction: String,
            override val progress: Float,
        ) : SlotState {
            override val color: BossBar.Color = BossBar.Color.GREEN
        }

        data class Downed(
            val name: String,
            val distance: Int,
            val direction: String,
        ) : SlotState {
            override val progress: Float = 0f
            override val color: BossBar.Color = BossBar.Color.RED
        }

        data class Dead(val name: String) : SlotState {
            override val progress: Float = 0f
            override val color: BossBar.Color = BossBar.Color.WHITE
        }

        data class Offline(val name: String) : SlotState {
            override val progress: Float = 0f
            override val color: BossBar.Color = BossBar.Color.WHITE
        }
    }

    internal fun computeRelativeBearing(viewerX: Double, viewerZ: Double, targetX: Double, targetZ: Double): String {
        val dx = targetX - viewerX
        val dz = targetZ - viewerZ
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 5.0) return "HERE"
        val angle = Math.toDegrees(atan2(-dx, dz)).let { if (it < 0) it + 360 else it }
        return when {
            angle < 22.5 || angle >= 337.5 -> "N"
            angle < 67.5 -> "NE"
            angle < 112.5 -> "E"
            angle < 157.5 -> "SE"
            angle < 202.5 -> "S"
            angle < 247.5 -> "SW"
            angle < 292.5 -> "W"
            else -> "NW"
        }
    }
}
