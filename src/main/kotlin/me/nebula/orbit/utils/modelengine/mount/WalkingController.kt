package me.nebula.orbit.utils.modelengine.mount

import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class WalkingController(
    private val speed: Double = 0.2,
    private val jumpVelocity: Double = 0.42,
    private val sprintMultiplier: Double = 1.5,
) : MountController {

    override fun tick(modeledEntity: ModeledEntity, driver: Player, input: MountInput) {
        val entity = modeledEntity.entityOrNull ?: return

        val driverYaw = driver.position.yaw()
        val yawRad = Math.toRadians(-driverYaw.toDouble())
        val sinYaw = sin(yawRad)
        val cosYaw = cos(yawRad)

        val forward = input.forward.toDouble()
        val sideways = input.sideways.toDouble()
        val mx = forward * sinYaw + sideways * cosYaw
        val mz = forward * cosYaw - sideways * sinYaw

        val effectiveSpeed = if (input.sprint && input.forward > 0f) speed * sprintMultiplier else speed

        val len = mx * mx + mz * mz
        val velocity = if (len > 0.001) {
            val inv = effectiveSpeed / sqrt(len)
            Vec(mx * inv, entity.velocity.y(), mz * inv)
        } else {
            Vec(0.0, entity.velocity.y(), 0.0)
        }

        val vy = if (input.jump && entity.isOnGround) jumpVelocity else velocity.y()
        entity.velocity = Vec(velocity.x(), vy, velocity.z())
        entity.setView(driverYaw, 0f)
    }
}
