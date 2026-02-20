package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import java.util.UUID

sealed interface BoneBehavior {
    val bone: ModelBone
    fun onAdd(modeledEntity: ModeledEntity) {}
    fun tick(modeledEntity: ModeledEntity) {}
    fun onRemove(modeledEntity: ModeledEntity) {}
    fun evictViewer(uuid: UUID) {}
}
