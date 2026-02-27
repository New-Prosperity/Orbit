package me.nebula.orbit.utils.modelengine.model

import me.nebula.orbit.utils.modelengine.animation.PriorityHandler
import me.nebula.orbit.utils.modelengine.behavior.BoneBehaviorFactory
import me.nebula.orbit.utils.modelengine.blueprint.ModelBlueprint
import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.render.BoneRenderer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player

class ActiveModel(val blueprint: ModelBlueprint, autoPlayIdle: Boolean = true) {

    val bones: Map<String, ModelBone>
    val rootBones: List<ModelBone>
    val renderer = BoneRenderer()
    val animationHandler: PriorityHandler = PriorityHandler()

    var modelScale: Float = 1.0f
        set(value) {
            field = value
            bones.values.forEach { bone ->
                bone.localScale = Vec(
                    bone.blueprint.scale.x() * value,
                    bone.blueprint.scale.y() * value,
                    bone.blueprint.scale.z() * value,
                )
            }
        }

    init {
        val boneMap = linkedMapOf<String, ModelBone>()
        blueprint.bones.forEach { (name, blueprintBone) ->
            boneMap[name] = ModelBone(blueprintBone)
        }

        boneMap.values.forEach { bone ->
            bone.blueprint.parentName?.let { parentName ->
                val parent = boneMap[parentName]
                bone.parent = parent
                parent?.children?.add(bone)
            }
            renderer.registerBone(bone)

            bone.blueprint.behaviors.forEach { (type, config) ->
                bone.addBehavior(BoneBehaviorFactory.create(type, bone, config))
            }
        }

        bones = boneMap
        rootBones = blueprint.rootBoneNames.mapNotNull { boneMap[it] }

        animationHandler.boundModel = this
        if (autoPlayIdle) {
            blueprint.animations.keys
                .firstOrNull { "idle" in it.lowercase() }
                ?.let { playAnimation(it) }
        }
    }

    fun bone(name: String): ModelBone = requireNotNull(bones[name]) { "Bone '$name' not found in model '${blueprint.name}'" }

    fun computeTransforms() {
        rootBones.forEach { it.computeTransform() }
    }

    fun updateRenderer(modelPosition: Pos) {
        renderer.update(modelPosition)
    }

    fun initBehaviors(modeledEntity: ModeledEntity) {
        bones.values.forEach { bone ->
            bone.behaviors.forEach { it.onAdd(modeledEntity) }
        }
    }

    fun tickBehaviors(modeledEntity: ModeledEntity) {
        bones.values.forEach { bone ->
            bone.behaviors.forEach { it.tick(modeledEntity) }
        }
    }

    fun destroyBehaviors(modeledEntity: ModeledEntity) {
        bones.values.forEach { bone ->
            bone.behaviors.forEach { it.onRemove(modeledEntity) }
        }
    }

    fun show(player: Player, modelPosition: Pos) {
        computeTransforms()
        renderer.show(player, modelPosition)
    }

    fun hide(player: Player) {
        renderer.hide(player)
    }

    fun destroy() {
        renderer.destroy()
    }

    fun evictBehaviorViewer(uuid: java.util.UUID) {
        bones.values.forEach { bone ->
            bone.behaviors.forEach { it.evictViewer(uuid) }
        }
    }

    fun resetAllAnimations() {
        bones.values.forEach { it.resetAnimation() }
    }

    fun tickAnimations(deltaSeconds: Float) {
        resetAllAnimations()
        animationHandler.tick(this, deltaSeconds)
    }

    fun playAnimation(name: String, lerpIn: Float = 0f, lerpOut: Float = 0f, speed: Float = 1f) {
        animationHandler.play(name, lerpIn, lerpOut, speed)
    }

    fun stopAnimation(name: String) {
        animationHandler.stop(name)
    }

    fun stopAllAnimations() {
        animationHandler.stopAll()
    }

    fun isPlayingAnimation(name: String): Boolean = animationHandler.isPlaying(name)
}
