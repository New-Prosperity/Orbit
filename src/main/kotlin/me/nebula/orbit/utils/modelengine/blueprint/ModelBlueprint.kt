package me.nebula.orbit.utils.modelengine.blueprint

import net.minestom.server.coordinate.Vec

data class AnimationBlueprint(
    val name: String,
    val length: Float,
    val loop: LoopMode,
    val boneKeyframes: Map<String, BoneKeyframes>,
)

enum class LoopMode { ONCE, LOOP, HOLD }

data class BoneKeyframes(
    val position: List<Keyframe>,
    val rotation: List<Keyframe>,
    val scale: List<Keyframe>,
)

data class Keyframe(
    val time: Float,
    val value: Vec,
    val interpolation: InterpolationType,
    val bezierLeftTime: Vec = Vec.ZERO,
    val bezierLeftValue: Vec = Vec.ZERO,
    val bezierRightTime: Vec = Vec.ZERO,
    val bezierRightValue: Vec = Vec.ZERO,
)

enum class InterpolationType { LINEAR, CATMULLROM, BEZIER, STEP }

data class ModelBlueprint(
    val name: String,
    val bones: Map<String, BlueprintBone>,
    val rootBoneNames: List<String>,
    val animations: Map<String, AnimationBlueprint>,
    val hitboxWidth: Float = 1f,
    val hitboxHeight: Float = 2f,
    val eyeHeight: Float = 1.6f,
) {
    fun bone(name: String): BlueprintBone = requireNotNull(bones[name]) { "Bone '$name' not found in blueprint '${this.name}'" }

    fun animation(name: String): AnimationBlueprint = requireNotNull(animations[name]) { "Animation '$name' not found in blueprint '${this.name}'" }

    fun traverseDepthFirst(action: (BlueprintBone, depth: Int) -> Unit) {
        fun visit(boneName: String, depth: Int) {
            val bone = bones[boneName] ?: return
            action(bone, depth)
            bone.childNames.forEach { visit(it, depth + 1) }
        }
        rootBoneNames.forEach { visit(it, 0) }
    }
}
