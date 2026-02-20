package me.nebula.orbit.utils.modelengine.generator

import net.minestom.server.coordinate.Vec

data class BlockbenchModel(
    val name: String,
    val meta: BbMeta,
    val resolution: BbResolution,
    val elements: List<BbElement>,
    val groups: List<BbGroup>,
    val textures: List<BbTexture>,
    val animations: List<BbAnimation>,
)

data class BbMeta(
    val formatVersion: String,
    val modelFormat: String,
    val boxUv: Boolean,
)

data class BbResolution(
    val width: Int,
    val height: Int,
)

data class BbElement(
    val uuid: String,
    val name: String,
    val from: Vec,
    val to: Vec,
    val origin: Vec,
    val rotation: Vec,
    val inflate: Float,
    val faces: Map<String, BbFace>,
    val visibility: Boolean,
)

data class BbFace(
    val uv: FloatArray,
    val texture: Int,
    val rotation: Int = 0,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BbFace) return false
        return uv.contentEquals(other.uv) && texture == other.texture && rotation == other.rotation
    }

    override fun hashCode(): Int {
        var result = uv.contentHashCode()
        result = 31 * result + texture
        result = 31 * result + rotation
        return result
    }
}

data class BbGroup(
    val uuid: String,
    val name: String,
    val origin: Vec,
    val rotation: Vec,
    val children: List<BbGroupChild>,
    val visibility: Boolean,
)

sealed interface BbGroupChild {
    data class ElementRef(val uuid: String) : BbGroupChild
    data class SubGroup(val group: BbGroup) : BbGroupChild
}

data class BbTexture(
    val id: Int,
    val name: String,
    val width: Int,
    val height: Int,
    val source: String,
)

data class BbAnimation(
    val name: String,
    val length: Float,
    val loop: String,
    val animators: Map<String, BbAnimator>,
)

data class BbAnimator(
    val name: String,
    val keyframes: List<BbKeyframe>,
)

data class BbKeyframe(
    val channel: String,
    val time: Float,
    val dataPoints: List<Vec>,
    val interpolation: String,
    val bezierLeftTime: Vec,
    val bezierLeftValue: Vec,
    val bezierRightTime: Vec,
    val bezierRightValue: Vec,
)
