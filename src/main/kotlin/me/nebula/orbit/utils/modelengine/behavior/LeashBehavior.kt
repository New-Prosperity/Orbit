package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.entity.Entity

class LeashBehavior(
    override val bone: ModelBone,
    private val maxDistance: Double = 10.0,
) : BoneBehavior {

    private var leashHandle: me.nebula.orbit.utils.entityleash.LeashHandle? = null
    private var targetEntity: Entity? = null

    fun attachTo(entity: Entity, modeledEntity: ModeledEntity) {
        detach()
        val holder = modeledEntity.entityOrNull
            ?: error("LeashBehavior requires Entity-backed owner")
        targetEntity = entity
        leashHandle = me.nebula.orbit.utils.entityleash.EntityLeashManager.leash(
            entity, holder, maxDistance,
        )
    }

    fun detach() {
        leashHandle?.release()
        leashHandle = null
        targetEntity = null
    }

    override fun onRemove(modeledEntity: ModeledEntity) {
        detach()
    }
}
