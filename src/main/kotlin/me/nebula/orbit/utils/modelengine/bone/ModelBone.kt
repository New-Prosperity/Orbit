package me.nebula.orbit.utils.modelengine.bone

import me.nebula.orbit.utils.modelengine.behavior.BoneBehavior
import me.nebula.orbit.utils.modelengine.blueprint.BlueprintBone
import me.nebula.orbit.utils.modelengine.math.*
import net.minestom.server.coordinate.Vec
import net.minestom.server.item.ItemStack

class ModelBone(val blueprint: BlueprintBone) {

    var parent: ModelBone? = null
        internal set

    val children: MutableList<ModelBone> = mutableListOf()

    @PublishedApi internal val _behaviors: MutableList<BoneBehavior> = mutableListOf()
    val behaviors: List<BoneBehavior> get() = _behaviors

    var localPosition: Vec = blueprint.offset
    var localRotation: Quat = blueprint.rotation
    var localScale: Vec = blueprint.scale

    var animatedPosition: Vec = Vec.ZERO
    var animatedRotation: Quat = QUAT_IDENTITY
    var animatedScale: Vec = Vec(1.0, 1.0, 1.0)

    var visible: Boolean = blueprint.visible
    var modelItem: ItemStack? = blueprint.modelItem

    var globalTransform: BoneTransform = BoneTransform.IDENTITY
        private set

    private var previousTransform: BoneTransform = BoneTransform.IDENTITY
    var dirty: Boolean = true
        internal set

    fun addBehavior(behavior: BoneBehavior) {
        _behaviors += behavior
    }

    fun removeBehavior(behavior: BoneBehavior) {
        _behaviors -= behavior
    }

    inline fun <reified T : BoneBehavior> behavior(): T? =
        _behaviors.firstOrNull { it is T } as? T

    inline fun <reified T : BoneBehavior> behaviorsOf(): List<T> =
        _behaviors.filterIsInstance<T>()

    fun computeTransform() {
        previousTransform = globalTransform

        val localTransform = BoneTransform(
            position = localPosition.add(animatedPosition),
            leftRotation = quatNormalize(quatMultiply(localRotation, animatedRotation)),
            scale = Vec(
                localScale.x() * animatedScale.x(),
                localScale.y() * animatedScale.y(),
                localScale.z() * animatedScale.z(),
            ),
        )

        globalTransform = parent?.let { localTransform.combine(it.globalTransform) } ?: localTransform
        dirty = globalTransform != previousTransform

        children.forEach { it.computeTransform() }
    }

    fun resetAnimation() {
        animatedPosition = Vec.ZERO
        animatedRotation = QUAT_IDENTITY
        localRotation = blueprint.rotation
        animatedScale = Vec(1.0, 1.0, 1.0)
    }
}
