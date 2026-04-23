package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.utils.customcontent.block.BlockHitbox

object HitboxInferrer {

    fun bestFit(cellLocal: CubeAabb): BlockHitbox {
        val span = CubeAabb(0.0, 0.0, 0.0, CELL.toDouble(), CELL.toDouble(), CELL.toDouble())
        val fillRatio = cellLocal.volumePixels / span.volumePixels
        if (fillRatio >= FULL_FILL_THRESHOLD) return BlockHitbox.Full

        val thickX = cellLocal.maxX - cellLocal.minX
        val thickY = cellLocal.maxY - cellLocal.minY
        val thickZ = cellLocal.maxZ - cellLocal.minZ

        val spansX = thickX >= CELL * WIDE_FILL_THRESHOLD
        val spansZ = thickZ >= CELL * WIDE_FILL_THRESHOLD

        if (spansX && spansZ) {
            if (thickY <= THIN_PIXELS) return BlockHitbox.Thin
            if (cellLocal.minY <= EDGE_EPSILON && thickY <= HALF + EDGE_EPSILON) return BlockHitbox.Slab
        }

        if (thickY >= CELL * WIDE_FILL_THRESHOLD) {
            if ((thickX <= NARROW_PIXELS || thickZ <= NARROW_PIXELS) && !(spansX && spansZ)) {
                return BlockHitbox.Fence
            }
        }

        if (thickY <= THIN_PIXELS || thickX <= THIN_PIXELS || thickZ <= THIN_PIXELS) {
            return BlockHitbox.Trapdoor
        }

        return BlockHitbox.Full
    }

    private const val CELL = 16
    private const val HALF = 8.0
    private const val THIN_PIXELS = 2.0
    private const val NARROW_PIXELS = 6.0
    private const val FULL_FILL_THRESHOLD = 0.90
    private const val WIDE_FILL_THRESHOLD = 0.85
    private const val EDGE_EPSILON = 0.0001
}
