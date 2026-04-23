package me.nebula.orbit.utils.customcontent.furniture

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import java.util.UUID

data class FurnitureInstance(
    val uuid: UUID,
    val definitionId: String,
    val instance: Instance,
    val anchorX: Int,
    val anchorY: Int,
    val anchorZ: Int,
    val yawDegrees: Float,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val displayEntityId: Int,
    val cellKeys: List<Long>,
    val interactionEntityIds: List<Int> = emptyList(),
    val owner: UUID? = null,
) {
    fun anchorVec(): Vec = Vec(anchorX.toDouble(), anchorY.toDouble(), anchorZ.toDouble())

    fun anchorPoint(): Point = Vec(anchorX.toDouble(), anchorY.toDouble(), anchorZ.toDouble())

    companion object {
        fun cellKeysOf(
            def: FurnitureDefinition,
            anchorX: Int,
            anchorY: Int,
            anchorZ: Int,
            quarterTurns: Int = 0,
        ): List<Long> {
            val rotated = FootprintRotation.rotate(def.footprint, quarterTurns)
            return rotated.cells.map { cell ->
                packKey(anchorX + cell.dx, anchorY + cell.dy, anchorZ + cell.dz)
            }
        }

        private const val X_BITS = 26
        private const val Y_BITS = 12
        private const val Z_BITS = 26
        private const val X_MASK = (1L shl X_BITS) - 1
        private const val Y_MASK = (1L shl Y_BITS) - 1
        private const val Z_MASK = (1L shl Z_BITS) - 1
        private const val Y_SHIFT = Z_BITS
        private const val X_SHIFT = Z_BITS + Y_BITS

        fun packKey(x: Int, y: Int, z: Int): Long =
            ((x.toLong() and X_MASK) shl X_SHIFT) or
                ((y.toLong() and Y_MASK) shl Y_SHIFT) or
                (z.toLong() and Z_MASK)

        fun unpackKey(packed: Long): Triple<Int, Int, Int> {
            val xRaw = ((packed shr X_SHIFT) and X_MASK).toInt()
            val yRaw = ((packed shr Y_SHIFT) and Y_MASK).toInt()
            val zRaw = (packed and Z_MASK).toInt()
            val x = if (xRaw and (1 shl (X_BITS - 1)) != 0) xRaw - (1 shl X_BITS) else xRaw
            val y = if (yRaw and (1 shl (Y_BITS - 1)) != 0) yRaw - (1 shl Y_BITS) else yRaw
            val z = if (zRaw and (1 shl (Z_BITS - 1)) != 0) zRaw - (1 shl Z_BITS) else zRaw
            return Triple(x, y, z)
        }
    }
}
