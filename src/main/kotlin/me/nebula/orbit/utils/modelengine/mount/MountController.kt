package me.nebula.orbit.utils.modelengine.mount

import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.entity.Player

sealed interface MountController {
    fun tick(modeledEntity: ModeledEntity, driver: Player, input: MountInput)
}

data class MountInput(
    val forward: Float = 0f,
    val sideways: Float = 0f,
    val jump: Boolean = false,
    val sneak: Boolean = false,
)
