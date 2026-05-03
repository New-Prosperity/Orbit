package me.nebula.orbit.utils.mapgen.planet

import kotlin.math.max
import kotlin.math.min

data class AABB(
    val x1: Int, val y1: Int, val z1: Int,
    val x2: Int, val y2: Int, val z2: Int,
) {
    init {
        require(x1 <= x2 && y1 <= y2 && z1 <= z2) { "AABB ($x1,$y1,$z1)..($x2,$y2,$z2) is degenerate" }
    }

    fun contains(x: Int, y: Int, z: Int): Boolean =
        x in x1..x2 && y in y1..y2 && z in z1..z2

    fun intersectsColumn(x: Int, z: Int): Boolean =
        x in x1..x2 && z in z1..z2

    fun intersectsChunk(cx: Int, cz: Int): Boolean {
        val cMinX = cx * 16
        val cMaxX = cMinX + 15
        val cMinZ = cz * 16
        val cMaxZ = cMinZ + 15
        return x1 <= cMaxX && x2 >= cMinX && z1 <= cMaxZ && z2 >= cMinZ
    }

    fun expandedXZ(by: Int): AABB =
        AABB(x1 - by, y1, z1 - by, x2 + by, y2, z2 + by)

    fun translated(dx: Int, dy: Int, dz: Int): AABB =
        AABB(x1 + dx, y1 + dy, z1 + dz, x2 + dx, y2 + dy, z2 + dz)

    val width: Int get() = x2 - x1 + 1
    val height: Int get() = y2 - y1 + 1
    val depth: Int get() = z2 - z1 + 1

    companion object {
        fun union(a: AABB, b: AABB): AABB = AABB(
            min(a.x1, b.x1), min(a.y1, b.y1), min(a.z1, b.z1),
            max(a.x2, b.x2), max(a.y2, b.y2), max(a.z2, b.z2),
        )
    }
}

data class Footprint(val x1: Int, val z1: Int, val x2: Int, val z2: Int) {
    fun contains(x: Int, z: Int): Boolean = x in x1..x2 && z in z1..z2

    fun distanceTo(x: Int, z: Int): Double {
        val dx = when {
            x < x1 -> (x1 - x).toDouble()
            x > x2 -> (x - x2).toDouble()
            else -> 0.0
        }
        val dz = when {
            z < z1 -> (z1 - z).toDouble()
            z > z2 -> (z - z2).toDouble()
            else -> 0.0
        }
        return kotlin.math.sqrt(dx * dx + dz * dz)
    }

    fun expand(by: Int): Footprint = Footprint(x1 - by, z1 - by, x2 + by, z2 + by)
}
