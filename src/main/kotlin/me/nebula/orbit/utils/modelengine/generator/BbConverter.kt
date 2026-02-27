package me.nebula.orbit.utils.modelengine.generator

import me.nebula.orbit.utils.modelengine.math.Quat
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import net.minestom.server.coordinate.Vec

object BbConverter {

    private const val BB_PIXELS_PER_BLOCK = 16.0

    fun pixelToBlock(bbPixels: Vec): Vec = Vec(
        bbPixels.x() / BB_PIXELS_PER_BLOCK,
        bbPixels.y() / BB_PIXELS_PER_BLOCK,
        bbPixels.z() / BB_PIXELS_PER_BLOCK,
    )

    fun boneOffset(boneOrigin: Vec, parentOrigin: Vec): Vec = pixelToBlock(
        Vec(
            -(boneOrigin.x() - parentOrigin.x()),
            boneOrigin.y() - parentOrigin.y(),
            -(boneOrigin.z() - parentOrigin.z()),
        )
    )

    fun boneRotation(bbEulerDeg: Vec): Quat = eulerToQuat(
        -bbEulerDeg.x().toFloat(),
        bbEulerDeg.y().toFloat(),
        -bbEulerDeg.z().toFloat(),
    )

    private const val MC_ELEMENT_MIN = -16f
    private const val MC_ELEMENT_MAX = 32f

    fun elementCoords(
        elementFrom: Vec,
        elementTo: Vec,
        boneOrigin: Vec,
        invScale: Float,
        centerOffset: Float,
    ): Pair<FloatArray, FloatArray> {
        fun coord(v: Double) = (v.toFloat() * invScale + centerOffset).coerceIn(MC_ELEMENT_MIN, MC_ELEMENT_MAX)
        val dx = elementFrom.x() - boneOrigin.x()
        val dy = elementFrom.y() - boneOrigin.y()
        val dz = elementFrom.z() - boneOrigin.z()
        val dtx = elementTo.x() - boneOrigin.x()
        val dty = elementTo.y() - boneOrigin.y()
        val dtz = elementTo.z() - boneOrigin.z()
        return floatArrayOf(coord(dx), coord(dy), coord(dz)) to floatArrayOf(coord(dtx), coord(dty), coord(dtz))
    }

    fun rotationOrigin(
        elementOrigin: Vec,
        boneOrigin: Vec,
        invScale: Float,
        centerOffset: Float,
    ): FloatArray = floatArrayOf(
        (elementOrigin.x() - boneOrigin.x()).toFloat() * invScale + centerOffset,
        (elementOrigin.y() - boneOrigin.y()).toFloat() * invScale + centerOffset,
        (elementOrigin.z() - boneOrigin.z()).toFloat() * invScale + centerOffset,
    )
}
