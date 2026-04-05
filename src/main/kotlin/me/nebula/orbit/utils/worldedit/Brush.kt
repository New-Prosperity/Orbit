package me.nebula.orbit.utils.worldedit

import me.nebula.orbit.utils.schematic.Schematic
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.block.Block
import kotlin.math.roundToInt

fun interface Brush {
    fun apply(session: EditSession, target: Pos, pattern: Pattern, size: Double)
}

data class BrushBinding(
    val brush: Brush,
    val pattern: Pattern,
    val mask: Mask? = null,
    val size: Double,
)

object Brushes {

    fun sphere(hollow: Boolean = false): Brush = Brush { session, target, pattern, size ->
        val r = size.roundToInt()
        val r2 = size * size
        val inner2 = if (hollow) (size - 1) * (size - 1) else -1.0
        val cx = target.blockX()
        val cy = target.blockY()
        val cz = target.blockZ()
        for (y in -r..r) {
            for (z in -r..r) {
                for (x in -r..r) {
                    val dist2 = (x * x + y * y + z * z).toDouble()
                    if (dist2 <= r2 && (!hollow || dist2 >= inner2)) {
                        session.setBlock(cx + x, cy + y, cz + z, pattern)
                    }
                }
            }
        }
    }

    fun cylinder(height: Int = 1): Brush = Brush { session, target, pattern, size ->
        val r = size.roundToInt()
        val r2 = size * size
        val cx = target.blockX()
        val cy = target.blockY()
        val cz = target.blockZ()
        for (y in 0 until height) {
            for (z in -r..r) {
                for (x in -r..r) {
                    if ((x * x + z * z).toDouble() <= r2) {
                        session.setBlock(cx + x, cy + y, cz + z, pattern)
                    }
                }
            }
        }
    }

    fun smooth(iterations: Int = 4): Brush = Brush { session, target, _, size ->
        val r = size.roundToInt()
        val sel = CuboidSelection(
            target.blockX() - r, target.blockY() - r, target.blockZ() - r,
            target.blockX() + r, target.blockY() + r, target.blockZ() + r,
        )
        EditOperations.smooth(session.instance, sel, iterations, session.player)
    }

    fun raise(): Brush = Brush { session, target, pattern, size ->
        val r = size.roundToInt()
        val cx = target.blockX()
        val cz = target.blockZ()
        for (x in -r..r) {
            for (z in -r..r) {
                if (x * x + z * z <= r * r) {
                    for (y in 319 downTo -64) {
                        val block = session.getBlock(cx + x, y, cz + z)
                        if (block.isSolid) {
                            session.setBlock(cx + x, y + 1, cz + z, pattern)
                            break
                        }
                    }
                }
            }
        }
    }

    fun lower(): Brush = Brush { session, target, _, size ->
        val r = size.roundToInt()
        val cx = target.blockX()
        val cz = target.blockZ()
        for (x in -r..r) {
            for (z in -r..r) {
                if (x * x + z * z <= r * r) {
                    for (y in 319 downTo -64) {
                        val block = session.getBlock(cx + x, y, cz + z)
                        if (block.isSolid) {
                            session.setBlock(cx + x, y, cz + z, Block.AIR)
                            break
                        }
                    }
                }
            }
        }
    }

    fun erode(): Brush = Brush { session, target, _, size ->
        val r = size.roundToInt()
        val cx = target.blockX()
        val cy = target.blockY()
        val cz = target.blockZ()
        val r2 = r * r
        val toRemove = mutableListOf<Triple<Int, Int, Int>>()

        for (y in -r..r) {
            for (z in -r..r) {
                for (x in -r..r) {
                    if (x * x + y * y + z * z > r2) continue
                    val bx = cx + x
                    val by = cy + y
                    val bz = cz + z
                    if (!session.getBlock(bx, by, bz).isSolid) continue

                    var airNeighbors = 0
                    if (!session.getBlock(bx + 1, by, bz).isSolid) airNeighbors++
                    if (!session.getBlock(bx - 1, by, bz).isSolid) airNeighbors++
                    if (!session.getBlock(bx, by + 1, bz).isSolid) airNeighbors++
                    if (!session.getBlock(bx, by - 1, bz).isSolid) airNeighbors++
                    if (!session.getBlock(bx, by, bz + 1).isSolid) airNeighbors++
                    if (!session.getBlock(bx, by, bz - 1).isSolid) airNeighbors++

                    if (airNeighbors >= 3) toRemove += Triple(bx, by, bz)
                }
            }
        }

        for ((bx, by, bz) in toRemove) {
            session.setBlock(bx, by, bz, Block.AIR)
        }
    }

    fun fill(): Brush = Brush { session, target, pattern, size ->
        val r = size.roundToInt()
        val cx = target.blockX()
        val cy = target.blockY()
        val cz = target.blockZ()
        for (x in -r..r) {
            for (z in -r..r) {
                if (x * x + z * z > r * r) continue
                for (y in cy downTo cy - r) {
                    if (session.getBlock(cx + x, y, cz + z).isAir) {
                        session.setBlock(cx + x, y, cz + z, pattern)
                    } else {
                        break
                    }
                }
            }
        }
    }

    fun paste(schematic: Schematic): Brush = Brush { session, target, _, _ ->
        for (y in 0 until schematic.height) {
            for (z in 0 until schematic.length) {
                for (x in 0 until schematic.width) {
                    val block = schematic.getBlock(x, y, z)
                    if (!block.isAir) {
                        session.setBlock(target.blockX() + x, target.blockY() + y, target.blockZ() + z, block)
                    }
                }
            }
        }
    }
}
