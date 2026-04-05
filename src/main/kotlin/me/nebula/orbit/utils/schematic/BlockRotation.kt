package me.nebula.orbit.utils.schematic

import net.minestom.server.instance.block.Block

enum class Rotation {
    NONE, CLOCKWISE_90, CLOCKWISE_180, COUNTERCLOCKWISE_90;

    val steps: Int get() = ordinal
}

enum class MirrorAxis { NONE, X, Z }

private val HORIZONTAL_FACINGS = listOf("north", "east", "south", "west")

fun rotateCoord(x: Int, z: Int, rotation: Rotation, width: Int, length: Int): Pair<Int, Int> =
    when (rotation) {
        Rotation.NONE -> x to z
        Rotation.CLOCKWISE_90 -> (length - 1 - z) to x
        Rotation.CLOCKWISE_180 -> (width - 1 - x) to (length - 1 - z)
        Rotation.COUNTERCLOCKWISE_90 -> z to (width - 1 - x)
    }

fun mirrorCoord(x: Int, z: Int, axis: MirrorAxis, width: Int, length: Int): Pair<Int, Int> =
    when (axis) {
        MirrorAxis.NONE -> x to z
        MirrorAxis.X -> x to (length - 1 - z)
        MirrorAxis.Z -> (width - 1 - x) to z
    }

fun rotatedSize(width: Int, length: Int, rotation: Rotation): Pair<Int, Int> =
    if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90) length to width
    else width to length

fun rotateBlock(block: Block, rotation: Rotation): Block {
    if (rotation == Rotation.NONE) return block
    var result = block

    val facing = block.getProperty("facing")
    if (facing != null && facing in HORIZONTAL_FACINGS) {
        result = result.withProperty("facing", rotateFacing(facing, rotation.steps))
    }

    val axis = block.getProperty("axis")
    if (axis != null && (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)) {
        result = when (axis) {
            "x" -> result.withProperty("axis", "z")
            "z" -> result.withProperty("axis", "x")
            else -> result
        }
    }

    val rot = block.getProperty("rotation")
    if (rot != null) {
        val rotVal = rot.toIntOrNull()
        if (rotVal != null) {
            result = result.withProperty("rotation", ((rotVal + rotation.steps * 4) % 16).toString())
        }
    }

    return result
}

fun mirrorBlock(block: Block, axis: MirrorAxis): Block {
    if (axis == MirrorAxis.NONE) return block
    var result = block

    val facing = block.getProperty("facing")
    if (facing != null && facing in HORIZONTAL_FACINGS) {
        result = when (axis) {
            MirrorAxis.X -> when (facing) {
                "north" -> result.withProperty("facing", "south")
                "south" -> result.withProperty("facing", "north")
                else -> result
            }
            MirrorAxis.Z -> when (facing) {
                "east" -> result.withProperty("facing", "west")
                "west" -> result.withProperty("facing", "east")
                else -> result
            }
            else -> result
        }
    }

    return result
}

private fun rotateFacing(facing: String, steps: Int): String {
    val idx = HORIZONTAL_FACINGS.indexOf(facing)
    if (idx == -1) return facing
    return HORIZONTAL_FACINGS[(idx + steps) % 4]
}
