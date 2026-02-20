package me.nebula.orbit.utils.modelengine.mount

import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import kotlin.math.cos
import kotlin.math.sin

class FlyingController(
    private val speed: Double = 0.3,
    private val verticalSpeed: Double = 0.2,
) : MountController {

    override fun tick(modeledEntity: ModeledEntity, driver: Player, input: MountInput) {
        val entity = modeledEntity.entityOrNull ?: return

        val yawRad = Math.toRadians(-driver.position.yaw().toDouble())
        val pitchRad = Math.toRadians(-driver.position.pitch().toDouble())
        val sinYaw = sin(yawRad)
        val cosYaw = cos(yawRad)
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)

        val forward = input.forward.toDouble()
        val sideways = input.sideways.toDouble()

        val mx = forward * sinYaw * cosPitch + sideways * cosYaw
        val mz = forward * cosYaw * cosPitch - sideways * sinYaw
        val my = if (input.jump) verticalSpeed else forward * sinPitch * speed

        val len = mx * mx + mz * mz
        val velocity = if (len > 0.001) {
            val inv = speed / kotlin.math.sqrt(len)
            Vec(mx * inv, my, mz * inv)
        } else {
            Vec(0.0, my, 0.0)
        }

        entity.velocity = velocity
    }
}
