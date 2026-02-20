package me.nebula.orbit.utils.modelengine.interaction

import me.nebula.orbit.utils.modelengine.behavior.SubHitboxBehavior
import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.trait.PlayerInstanceEvent

data class ModelDamageEvent(
    val attacker: Player,
    val modeledEntity: ModeledEntity,
    val bone: ModelBone,
    val hitbox: SubHitboxBehavior?,
    val hitDistance: Double,
    var damage: Float,
    var cancelled: Boolean = false,
) : PlayerInstanceEvent {
    override fun getPlayer(): Player = attacker
}
