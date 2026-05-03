package me.nebula.orbit.utils.customcontent.helditem

import net.minestom.server.coordinate.Vec

enum class AnimationChannel { POSITION, ROTATION, SCALE }

enum class AnimationInterpolation { LINEAR, STEP, CATMULLROM, BEZIER }

enum class AnimationLoopMode { ONCE, LOOP, HOLD }

enum class AnimationTrigger { IDLE, SWING, USE }

data class HeldItemKeyframe(
    val time: Float,
    val value: Vec,
    val interpolation: AnimationInterpolation,
    val bezierLeftTime: Vec = Vec.ZERO,
    val bezierLeftValue: Vec = Vec.ZERO,
    val bezierRightTime: Vec = Vec.ZERO,
    val bezierRightValue: Vec = Vec.ZERO,
)

data class HeldItemBoneTrack(
    val position: List<HeldItemKeyframe>,
    val rotation: List<HeldItemKeyframe>,
    val scale: List<HeldItemKeyframe>,
) {
    val isEmpty: Boolean get() = position.isEmpty() && rotation.isEmpty() && scale.isEmpty()
}

data class HeldItemCubeFace(
    val direction: String,
    val uMin: Float,
    val vMin: Float,
    val uMax: Float,
    val vMax: Float,
    val rotation: Int,
    val textureIndex: Int,
)

data class HeldItemCube(
    val from: Vec,
    val to: Vec,
    val origin: Vec,
    val rotation: Vec,
    val inflate: Float,
    val faces: List<HeldItemCubeFace>,
)

data class HeldItemBone(
    val id: Int,
    val name: String,
    val parentId: Int,
    val pivot: Vec,
    val baseRotation: Vec,
    val cubes: List<HeldItemCube> = emptyList(),
)

data class HeldItemTexture(
    val index: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val source: String,
)

data class HeldItemAnimation(
    val name: String,
    val trigger: AnimationTrigger,
    val length: Float,
    val loopMode: AnimationLoopMode,
    val tracks: Map<Int, HeldItemBoneTrack>,
)

data class HeldItemDisplaySlot(
    val rotation: FloatArray,
    val translation: FloatArray,
    val scale: FloatArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeldItemDisplaySlot) return false
        return rotation.contentEquals(other.rotation) &&
            translation.contentEquals(other.translation) &&
            scale.contentEquals(other.scale)
    }

    override fun hashCode(): Int {
        var result = rotation.contentHashCode()
        result = 31 * result + translation.contentHashCode()
        result = 31 * result + scale.contentHashCode()
        return result
    }
}

data class ParsedHeldItem(
    val id: String,
    val bones: List<HeldItemBone>,
    val animations: List<HeldItemAnimation>,
    val textures: List<HeldItemTexture>,
    val resolutionWidth: Int,
    val resolutionHeight: Int,
    val displaySlots: Map<String, HeldItemDisplaySlot> = emptyMap(),
) {
    val boneById: Map<Int, HeldItemBone> = bones.associateBy { it.id }

    fun animation(trigger: AnimationTrigger): HeldItemAnimation? =
        animations.firstOrNull { it.trigger == trigger }
}

data class RegisteredHeldItem(
    val id: String,
    val colorId: Int,
    val colorR: Int,
    val colorG: Int,
    val colorB: Int,
    val parsed: ParsedHeldItem,
    val modelScale: Float = 1f,
) {
    val dyeRgb: Int get() = (colorR shl 16) or (colorG shl 8) or colorB

    fun triggerColor(trigger: AnimationTrigger): Int {
        val flag = when (trigger) {
            AnimationTrigger.IDLE -> 0
            AnimationTrigger.SWING -> 1
            AnimationTrigger.USE -> 2
        }
        val r = (colorR and 0xFC) or flag
        return (r shl 16) or (colorG shl 8) or colorB
    }
}
