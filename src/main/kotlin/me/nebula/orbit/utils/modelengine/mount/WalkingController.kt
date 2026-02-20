package me.nebula.orbit.utils.modelengine.mount

import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import kotlin.math.cos
import kotlin.math.sin

class WalkingController(
    private val speed: Double = 0.2,
    private val jumpVelocity: Double = 0.42,
) : MountController {

    override fun tick(modeledEntity: ModeledEntity, driver: Player, input: MountInput) {
        val entity = modeledEntity.entityOrNull ?: return

        val yawRad = Math.toRadians(-driver.position.yaw().toDouble())
        val sinYaw = sin(yawRad)
        val cosYaw = cos(yawRad)

        val forward = input.forward.toDouble()
        val sideways = input.sideways.toDouble()
        val mx = forward * sinYaw + sideways * cosYaw
        val mz = forward * cosYaw - sideways * sinYaw

        val len = mx * mx + mz * mz
        val velocity = if (len > 0.001) {
            val inv = speed / kotlin.math.sqrt(len)
            Vec(mx * inv, entity.velocity.y(), mz * inv)
        } else {
            Vec(0.0, entity.velocity.y(), 0.0)
        }

        val vy = if (input.jump && entity.isOnGround) jumpVelocity else velocity.y()
        entity.velocity = Vec(velocity.x(), vy, velocity.z())
    }
}
