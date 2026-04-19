package me.nebula.orbit.utils.anticheat.checks

import me.nebula.gravity.config.ConfigStore
import me.nebula.gravity.config.NetworkConfig
import me.nebula.orbit.utils.anticheat.AntiCheat
import me.nebula.orbit.utils.anticheat.AntiCheatCheck
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object KillAuraCheck : AntiCheatCheck {

    override val id: String = "killaura"

    private const val MAX_ANGLE_DEGREES = 75.0
    private const val WEIGHT = 3

    override fun install(node: EventNode<in Event>) {
        node.addListener(EntityAttackEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            if (player.gameMode == GameMode.CREATIVE) return@addListener
            if (!ConfigStore.get(NetworkConfig.AC_CHECK_KILLAURA_ENABLED)) return@addListener

            val target = event.target
            val eye = player.position.add(0.0, player.eyeHeight, 0.0)
            val dx = target.position.x() - eye.x()
            val dy = target.position.y() - eye.y()
            val dz = target.position.z() - eye.z()
            val targetDist = sqrt(dx * dx + dy * dy + dz * dz)
            if (targetDist < 0.5) return@addListener

            val yawRad = Math.toRadians((-player.position.yaw() - 90.0))
            val pitchRad = Math.toRadians(-player.position.pitch().toDouble())
            val lookX = cos(pitchRad) * cos(yawRad)
            val lookY = sin(pitchRad)
            val lookZ = cos(pitchRad) * sin(yawRad)

            val nx = dx / targetDist
            val ny = dy / targetDist
            val nz = dz / targetDist
            val dot = lookX * nx + lookY * ny + lookZ * nz
            val angleRad = acos(dot.coerceIn(-1.0, 1.0))
            val angleDegrees = Math.toDegrees(angleRad)

            if (angleDegrees > MAX_ANGLE_DEGREES) {
                AntiCheat.flag(player.uuid, "killaura", WEIGHT, AntiCheat.combatFlagThreshold, AntiCheat.combatKickThreshold)
            }
        }
    }

    override fun cleanup(uuid: UUID) {}

    override fun clearAll() {}
}
