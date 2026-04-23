package me.nebula.orbit.utils.customcontent.furniture

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class CubeAabb(
    val minX: Double, val minY: Double, val minZ: Double,
    val maxX: Double, val maxY: Double, val maxZ: Double,
) {
    init {
        require(minX <= maxX && minY <= maxY && minZ <= maxZ) {
            "Invalid AABB: min must be <= max ($minX,$minY,$minZ) -> ($maxX,$maxY,$maxZ)"
        }
    }

    fun clipToCell(cell: FootprintCell): CubeAabb? {
        val cellMinX = cell.dx * PIXELS_PER_BLOCK.toDouble()
        val cellMinY = cell.dy * PIXELS_PER_BLOCK.toDouble()
        val cellMinZ = cell.dz * PIXELS_PER_BLOCK.toDouble()
        val cellMaxX = cellMinX + PIXELS_PER_BLOCK
        val cellMaxY = cellMinY + PIXELS_PER_BLOCK
        val cellMaxZ = cellMinZ + PIXELS_PER_BLOCK

        val clippedMinX = max(minX, cellMinX)
        val clippedMinY = max(minY, cellMinY)
        val clippedMinZ = max(minZ, cellMinZ)
        val clippedMaxX = min(maxX, cellMaxX)
        val clippedMaxY = min(maxY, cellMaxY)
        val clippedMaxZ = min(maxZ, cellMaxZ)

        if (clippedMinX >= clippedMaxX || clippedMinY >= clippedMaxY || clippedMinZ >= clippedMaxZ) {
            return null
        }
        return CubeAabb(clippedMinX, clippedMinY, clippedMinZ, clippedMaxX, clippedMaxY, clippedMaxZ)
    }

    fun toCellLocal(cell: FootprintCell): CubeAabb {
        val offsetX = cell.dx * PIXELS_PER_BLOCK.toDouble()
        val offsetY = cell.dy * PIXELS_PER_BLOCK.toDouble()
        val offsetZ = cell.dz * PIXELS_PER_BLOCK.toDouble()
        return CubeAabb(
            minX - offsetX, minY - offsetY, minZ - offsetZ,
            maxX - offsetX, maxY - offsetY, maxZ - offsetZ,
        )
    }

    fun cellsTouched(): List<FootprintCell> {
        val minCellX = floor(minX / PIXELS_PER_BLOCK).toInt()
        val minCellY = floor(minY / PIXELS_PER_BLOCK).toInt()
        val minCellZ = floor(minZ / PIXELS_PER_BLOCK).toInt()
        val maxCellX = floor((maxX - EDGE_EPSILON) / PIXELS_PER_BLOCK).toInt()
        val maxCellY = floor((maxY - EDGE_EPSILON) / PIXELS_PER_BLOCK).toInt()
        val maxCellZ = floor((maxZ - EDGE_EPSILON) / PIXELS_PER_BLOCK).toInt()
        val cells = mutableListOf<FootprintCell>()
        for (cx in minCellX..maxCellX) {
            for (cy in minCellY..maxCellY) {
                for (cz in minCellZ..maxCellZ) {
                    cells += FootprintCell(cx, cy, cz)
                }
            }
        }
        return cells
    }

    fun union(other: CubeAabb): CubeAabb = CubeAabb(
        min(minX, other.minX), min(minY, other.minY), min(minZ, other.minZ),
        max(maxX, other.maxX), max(maxY, other.maxY), max(maxZ, other.maxZ),
    )

    val volumePixels: Double get() = (maxX - minX) * (maxY - minY) * (maxZ - minZ)

    companion object {
        const val PIXELS_PER_BLOCK: Int = 16
        private const val EDGE_EPSILON = 0.0001
    }
}
