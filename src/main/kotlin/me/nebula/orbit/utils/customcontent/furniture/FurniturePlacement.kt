package me.nebula.orbit.utils.customcontent.furniture

import net.minestom.server.instance.block.BlockFace

data class FurniturePlacement(
    val allowedFaces: Set<BlockFace>,
    val autoOrient: Boolean,
) {
    init {
        require(allowedFaces.isNotEmpty()) { "FurniturePlacement.allowedFaces must not be empty" }
    }

    fun allows(face: BlockFace): Boolean = face in allowedFaces

    companion object {
        val FLOOR = FurniturePlacement(setOf(BlockFace.TOP), autoOrient = false)
        val CEILING = FurniturePlacement(setOf(BlockFace.TOP, BlockFace.BOTTOM), autoOrient = true)
        val WALL = FurniturePlacement(
            setOf(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST),
            autoOrient = true,
        )
        val ANY_AXIS = FurniturePlacement(BlockFace.values().toSet(), autoOrient = true)

        fun byName(name: String): FurniturePlacement? = when (name.lowercase()) {
            "floor" -> FLOOR
            "ceiling" -> CEILING
            "wall" -> WALL
            "any", "any_axis", "anyaxis" -> ANY_AXIS
            else -> null
        }
    }
}

object FurniturePlacementRotation {

    data class Euler(val yawDegrees: Float, val pitchDegrees: Float, val rollDegrees: Float)

    fun eulerFor(face: BlockFace, yawDegrees: Float, autoOrient: Boolean): Euler {
        if (!autoOrient) return Euler(yawDegrees, 0f, 0f)
        return when (face) {
            BlockFace.TOP -> Euler(yawDegrees, 0f, 0f)
            BlockFace.BOTTOM -> Euler(yawDegrees, 180f, 0f)
            BlockFace.NORTH -> Euler(0f, -90f, 0f)
            BlockFace.SOUTH -> Euler(0f, 90f, 0f)
            BlockFace.EAST -> Euler(0f, 0f, -90f)
            BlockFace.WEST -> Euler(0f, 0f, 90f)
        }
    }
}
