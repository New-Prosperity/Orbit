package me.nebula.orbit.utils.customcontent.furniture

object FootprintRotation {

    fun rotate(footprint: FurnitureFootprint, quarterTurns: Int): FurnitureFootprint {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) return footprint
        val rotated = footprint.cells.map { cell ->
            when (turns) {
                1 -> FootprintCell(-cell.dz, cell.dy, cell.dx)
                2 -> FootprintCell(-cell.dx, cell.dy, -cell.dz)
                3 -> FootprintCell(cell.dz, cell.dy, -cell.dx)
                else -> cell
            }
        }
        return FurnitureFootprint(rotated)
    }

    fun yawToQuarterTurns(yawDegrees: Float): Int {
        val normalized = ((yawDegrees % 360f) + 360f) % 360f
        return (((normalized + 45f) / 90f).toInt()) % 4
    }

    fun snapYaw(yawDegrees: Float, snapDegrees: Double): Float {
        if (snapDegrees <= 0.0) return yawDegrees
        val normalized = ((yawDegrees % 360f) + 360f) % 360f
        val steps = normalized / snapDegrees
        val snapped = kotlin.math.round(steps) * snapDegrees
        return (snapped % 360.0).toFloat()
    }
}
