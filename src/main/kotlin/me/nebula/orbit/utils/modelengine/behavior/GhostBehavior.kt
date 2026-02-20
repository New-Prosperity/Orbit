package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity

class GhostBehavior(
    override val bone: ModelBone,
) : BoneBehavior {

    override fun onAdd(modeledEntity: ModeledEntity) {
        bone.visible = false
    }

    override fun onRemove(modeledEntity: ModeledEntity) {
        bone.visible = bone.blueprint.visible
    }
}
