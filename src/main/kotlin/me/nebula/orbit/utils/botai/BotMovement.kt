package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.atan2
import kotlin.math.sqrt

object BotMovement {

    const val WALK_SPEED = 0.2158
    const val SPRINT_SPEED = 0.2806
    const val VELOCITY_SCALE = 25.0
    const val JUMP_IMPULSE = 8.0

    fun moveToward(player: Player, target: Point, sprint: Boolean, jitter: Float = 0f) {
        val rng = ThreadLocalRandom.current()
        val jitterX = if (jitter > 0f) rng.nextGaussian() * jitter else 0.0
        val jitterZ = if (jitter > 0f) rng.nextGaussian() * jitter else 0.0
        val dx = target.x() + jitterX - player.position.x()
        val dz = target.z() + jitterZ - player.position.z()
        val dist = sqrt(dx * dx + dz * dz)
        if (dist < 0.1) return
        val normX = dx / dist
        val normZ = dz / dist
        player.isSprinting = sprint
        val speed = (if (sprint) SPRINT_SPEED else WALK_SPEED) * VELOCITY_SCALE
        player.velocity = Vec(normX * speed, player.velocity.y(), normZ * speed)
        val yaw = -Math.toDegrees(atan2(normX, normZ)).toFloat()
        player.setView(yaw, 0f)
    }

    fun lookAt(player: Player, target: Point) {
        val eyePos = player.position.add(0.0, player.eyeHeight, 0.0)
        val dx = target.x() - eyePos.x()
        val dy = target.y() - eyePos.y()
        val dz = target.z() - eyePos.z()
        val horizontalDist = sqrt(dx * dx + dz * dz)
        if (horizontalDist < 0.001) return
        val yaw = -Math.toDegrees(atan2(dx, dz)).toFloat()
        val pitch = -Math.toDegrees(atan2(dy, horizontalDist)).toFloat()
        player.setView(yaw, pitch)
    }

    fun jump(player: Player) {
        if (player.isOnGround) {
            player.velocity = Vec(player.velocity.x(), JUMP_IMPULSE, player.velocity.z())
        }
    }
}
