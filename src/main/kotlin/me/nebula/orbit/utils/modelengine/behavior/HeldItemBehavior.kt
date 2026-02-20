package me.nebula.orbit.utils.modelengine.behavior

import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.item.ItemStack

class HeldItemBehavior(
    override val bone: ModelBone,
    item: ItemStack,
) : BoneBehavior {

    var item: ItemStack = item
        set(value) {
            field = value
            bone.modelItem = value
        }

    override fun onAdd(modeledEntity: ModeledEntity) {
        bone.modelItem = item
    }

    override fun onRemove(modeledEntity: ModeledEntity) {
        bone.modelItem = bone.blueprint.modelItem
    }
}
