package me.nebula.orbit.mode.game.battleroyale.spawn

import me.nebula.orbit.mode.game.battleroyale.SpawnModeExecutor
import me.nebula.orbit.mode.game.battleroyale.SpawnModeResult
import me.nebula.orbit.mode.game.battleroyale.asJavaRandom
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

object HungerGamesProvider : SpawnModeProvider {
    override val id: String = "hunger_games"
    override fun execute(context: SpawnContext): SpawnModeResult =
        SpawnModeExecutor.ring(context, context.config.ringRadius)
}

object ExtendedHungerGamesProvider : SpawnModeProvider {
    override val id: String = "extended_hunger_games"
    override fun execute(context: SpawnContext): SpawnModeResult =
        SpawnModeExecutor.ring(context, context.config.extendedRingRadius)
}

object RandomSpawnProvider : SpawnModeProvider {
    override val id: String = "random"
    override fun execute(context: SpawnContext): SpawnModeResult =
        SpawnModeExecutor.random(context)
}

object BattleBusProvider : SpawnModeProvider {
    override val id: String = "battle_royale_bus"
    override fun execute(context: SpawnContext): SpawnModeResult =
        SpawnModeExecutor.bus(context)
}

object PodDropProvider : SpawnModeProvider {

    override val id: String = "pod_drop"

    override fun execute(context: SpawnContext): SpawnModeResult {
        val config = context.config
        val players = context.players
        if (players.isEmpty()) return SpawnModeResult(pvpBlocked = false)

        val radius = config.podSpacingRadius.coerceAtMost(context.mapRadius - config.edgeMargin)
        val angleStep = 2 * Math.PI / players.size
        val shuffled = players.shuffled(context.random.asJavaRandom())
        val immunities = mutableSetOf<UUID>()

        shuffled.forEachIndexed { index, player ->
            val angle = angleStep * index
            val x = context.center.x() + cos(angle) * radius
            val z = context.center.z() + sin(angle) * radius
            val yaw = Math.toDegrees(-angle + Math.PI).toFloat()
            val pos = Pos(x, config.podHeight, z, yaw, 0f)
            context.onPlayerReady(player, pos)
            context.onImmunityGrant(player.uuid, pos)
            player.addEffect(Potion(PotionEffect.SLOW_FALLING, 0, config.parachuteDurationTicks))
            immunities += player.uuid
        }

        context.onComplete?.invoke()
        return SpawnModeResult(pvpBlocked = true, immunityPlayers = immunities)
    }
}

object TeamClusterProvider : SpawnModeProvider {

    override val id: String = "team_cluster"

    override fun execute(context: SpawnContext): SpawnModeResult {
        val config = context.config
        val players = context.players
        if (players.isEmpty()) return SpawnModeResult(pvpBlocked = false)

        val teams = players.groupBy { context.teamOf(it.uuid) ?: it.uuid.toString() }.toList()
        val clusterRadius = config.teamClusterRadius.coerceAtMost(context.mapRadius - config.edgeMargin)
        val angleStep = 2 * Math.PI / teams.size
        val immunities = mutableSetOf<UUID>()

        teams.forEachIndexed { index, (_, members) ->
            val angle = angleStep * index
            val clusterCenterX = context.center.x() + cos(angle) * clusterRadius
            val clusterCenterZ = context.center.z() + sin(angle) * clusterRadius
            val memberAngleStep = if (members.size <= 1) 0.0 else 2 * Math.PI / members.size
            members.forEachIndexed { memberIndex, player ->
                val memberAngle = memberAngleStep * memberIndex
                val px = clusterCenterX + cos(memberAngle) * config.teamClusterSpacing
                val pz = clusterCenterZ + sin(memberAngle) * config.teamClusterSpacing
                val surfaceY = SpawnModeExecutor.findSurfaceHeight(context.instance, px.toInt(), pz.toInt())
                val facingAngle = Math.toDegrees(-angle + Math.PI).toFloat()
                val pos = Pos(px, surfaceY + 1.0, pz, facingAngle, 0f)
                context.onPlayerReady(player, pos)
                context.onImmunityGrant(player.uuid, pos)
                immunities += player.uuid
            }
        }

        return SpawnModeResult(pvpBlocked = false, immunityPlayers = immunities)
    }
}

object ThemedRingProvider : SpawnModeProvider {

    override val id: String = "themed_ring"

    override fun execute(context: SpawnContext): SpawnModeResult {
        val config = context.config
        val players = context.players
        if (players.isEmpty()) return SpawnModeResult(pvpBlocked = false)

        val radius = (context.mapRadius - config.edgeMargin).coerceAtLeast(config.ringRadius)
        val angleStep = 2 * Math.PI / players.size
        val shuffled = players.shuffled(context.random.asJavaRandom())
        val immunities = mutableSetOf<UUID>()

        shuffled.forEachIndexed { index, player ->
            val angle = angleStep * index
            val x = context.center.x() + cos(angle) * radius
            val z = context.center.z() + sin(angle) * radius
            val surfaceY = SpawnModeExecutor.findSurfaceHeight(context.instance, x.toInt(), z.toInt())
            val facingYaw = if (config.themedRingFacingInward) {
                Math.toDegrees(-angle + Math.PI).toFloat()
            } else {
                Math.toDegrees(-angle).toFloat()
            }
            val pos = Pos(x, surfaceY + 1.0, z, facingYaw, 0f)
            context.onPlayerReady(player, pos)
            context.onImmunityGrant(player.uuid, pos)
            immunities += player.uuid
        }

        return SpawnModeResult(pvpBlocked = false, immunityPlayers = immunities)
    }
}
