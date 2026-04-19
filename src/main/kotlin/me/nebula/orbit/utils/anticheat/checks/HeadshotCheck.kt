package me.nebula.orbit.utils.anticheat.checks

import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.NetworkConfig
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.anticheat.AntiCheatCheck
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

object HeadshotCheck : AntiCheatCheck {

    override val id: String = "headshot"

    private const val SAMPLE_THRESHOLD = 50
    private const val HEADSHOT_RATIO_THRESHOLD = 0.60
    private const val HEAD_REGION_Y = 0.75
    private const val WEIGHT = 4

    private data class Stats(var hits: Int = 0, var headHits: Int = 0)

    private val stats = ConcurrentHashMap<UUID, Stats>()
    private val flagged = ConcurrentHashMap.newKeySet<UUID>()

    override fun install(node: EventNode<in Event>) {
        node.addListener(EntityAttackEvent::class.java) { event ->
            if (!ConfigStore.get(NetworkConfig.AC_CHECK_HEADSHOT_ENABLED)) return@addListener
            val attacker = event.entity as? Player ?: return@addListener
            if (attacker.gameMode == GameMode.CREATIVE) return@addListener
            val target = event.target as? LivingEntity ?: return@addListener
            if (target.uuid == attacker.uuid) return@addListener

            val stat = stats.computeIfAbsent(attacker.uuid) { Stats() }
            stat.hits++
            if (isHeadAimed(attacker, target)) stat.headHits++

            if (stat.hits >= SAMPLE_THRESHOLD && flagged.add(attacker.uuid)) {
                val ratio = stat.headHits.toDouble() / stat.hits
                if (ratio > HEADSHOT_RATIO_THRESHOLD) {
                    AntiCheat.flag(
                        attacker.uuid, "headshot", WEIGHT,
                        AntiCheat.combatFlagThreshold, AntiCheat.combatKickThreshold,
                    )
                } else {
                    flagged.remove(attacker.uuid)
                    stat.hits = 0
                    stat.headHits = 0
                }
            }
        }
    }

    private fun isHeadAimed(attacker: Player, target: LivingEntity): Boolean {
        val eyeX = attacker.position.x()
        val eyeY = attacker.position.y() + attacker.eyeHeight
        val eyeZ = attacker.position.z()

        val dx = target.position.x() - eyeX
        val dz = target.position.z() - eyeZ
        val horizontalDist = kotlin.math.sqrt(dx * dx + dz * dz)
        if (horizontalDist < 0.05) return false

        val yawRad = Math.toRadians((-attacker.position.yaw() - 90.0))
        val pitchRad = Math.toRadians(-attacker.position.pitch().toDouble())
        val lookX = cos(pitchRad) * cos(yawRad)
        val lookY = sin(pitchRad)
        val lookZ = cos(pitchRad) * sin(yawRad)

        val lookHorizontal = kotlin.math.sqrt(lookX * lookX + lookZ * lookZ)
        if (lookHorizontal < 0.0001) return false

        val t = horizontalDist / lookHorizontal
        val hitY = eyeY + lookY * t

        val targetHeight = target.boundingBox.height()
        val relY = (hitY - target.position.y()) / targetHeight
        return relY >= HEAD_REGION_Y && relY <= 1.1
    }

    override fun cleanup(uuid: UUID) {
        stats.remove(uuid)
        flagged.remove(uuid)
    }

    override fun clearAll() {
        stats.clear()
        flagged.clear()
    }
}
