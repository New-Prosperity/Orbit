package me.nebula.orbit.utils.worldedit

import me.nebula.orbit.utils.schematic.MirrorAxis
import me.nebula.orbit.utils.schematic.Rotation
import me.nebula.orbit.utils.schematic.Schematic
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

object EditOperations {

    fun set(instance: Instance, sel: CuboidSelection, pattern: Pattern, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, sel.volume.toInt())
        for (y in sel.minY..sel.maxY) {
            for (z in sel.minZ..sel.maxZ) {
                for (x in sel.minX..sel.maxX) {
                    session.setBlock(x, y, z, pattern)
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun replace(instance: Instance, sel: CuboidSelection, mask: Mask, pattern: Pattern, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, sel.volume.toInt())
        for (y in sel.minY..sel.maxY) {
            for (z in sel.minZ..sel.maxZ) {
                for (x in sel.minX..sel.maxX) {
                    val current = instance.getBlock(x, y, z)
                    if (mask.test(current.stateId())) {
                        session.setBlock(x, y, z, pattern)
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun copy(instance: Instance, sel: CuboidSelection, origin: Pos): Schematic =
        Schematic.copy(instance, Pos(sel.minX.toDouble(), sel.minY.toDouble(), sel.minZ.toDouble()),
            Pos(sel.maxX.toDouble(), sel.maxY.toDouble(), sel.maxZ.toDouble()))

    fun paste(instance: Instance, schematic: Schematic, origin: Pos, rotation: Rotation = Rotation.NONE, mirror: MirrorAxis = MirrorAxis.NONE, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, schematic.size)
        val rotated = schematic.rotated(rotation).mirrored(mirror)
        for (y in 0 until rotated.height) {
            for (z in 0 until rotated.length) {
                for (x in 0 until rotated.width) {
                    val block = rotated.getBlock(x, y, z)
                    if (!block.isAir) {
                        session.setBlock(origin.blockX() + x, origin.blockY() + y, origin.blockZ() + z, block)
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun move(instance: Instance, sel: CuboidSelection, direction: Vec, distance: Int, player: Player? = null): Pair<EditResult, ChangeSet> {
        val schematic = copy(instance, sel, Pos(sel.minX.toDouble(), sel.minY.toDouble(), sel.minZ.toDouble()))
        val session = EditSession(instance, player, sel.volume.toInt() * 2)
        for (y in sel.minY..sel.maxY) {
            for (z in sel.minZ..sel.maxZ) {
                for (x in sel.minX..sel.maxX) {
                    session.setBlock(x, y, z, Block.AIR)
                }
            }
        }
        val offset = direction.mul(distance.toDouble())
        for (y in 0 until schematic.height) {
            for (z in 0 until schematic.length) {
                for (x in 0 until schematic.width) {
                    val block = schematic.getBlock(x, y, z)
                    if (!block.isAir) {
                        session.setBlock(
                            sel.minX + x + offset.blockX(),
                            sel.minY + y + offset.blockY(),
                            sel.minZ + z + offset.blockZ(),
                            block,
                        )
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun stack(instance: Instance, sel: CuboidSelection, direction: Vec, count: Int, player: Player? = null): Pair<EditResult, ChangeSet> {
        val schematic = copy(instance, sel, Pos(sel.minX.toDouble(), sel.minY.toDouble(), sel.minZ.toDouble()))
        val session = EditSession(instance, player, sel.volume.toInt() * count)
        for (i in 1..count) {
            val offset = direction.mul((i * maxOf(sel.width, sel.height, sel.length)).toDouble())
            for (y in 0 until schematic.height) {
                for (z in 0 until schematic.length) {
                    for (x in 0 until schematic.width) {
                        val block = schematic.getBlock(x, y, z)
                        if (!block.isAir) {
                            session.setBlock(
                                sel.minX + x + offset.blockX(),
                                sel.minY + y + offset.blockY(),
                                sel.minZ + z + offset.blockZ(),
                                block,
                            )
                        }
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun walls(instance: Instance, sel: CuboidSelection, pattern: Pattern, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, sel.volume.toInt())
        for (y in sel.minY..sel.maxY) {
            for (x in sel.minX..sel.maxX) {
                session.setBlock(x, y, sel.minZ, pattern)
                session.setBlock(x, y, sel.maxZ, pattern)
            }
            for (z in sel.minZ..sel.maxZ) {
                session.setBlock(sel.minX, y, z, pattern)
                session.setBlock(sel.maxX, y, z, pattern)
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun outline(instance: Instance, sel: CuboidSelection, pattern: Pattern, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, sel.volume.toInt())
        for (y in sel.minY..sel.maxY) {
            for (z in sel.minZ..sel.maxZ) {
                for (x in sel.minX..sel.maxX) {
                    if (x == sel.minX || x == sel.maxX || y == sel.minY || y == sel.maxY || z == sel.minZ || z == sel.maxZ) {
                        session.setBlock(x, y, z, pattern)
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun drain(instance: Instance, sel: CuboidSelection, player: Player? = null): Pair<EditResult, ChangeSet> =
        replace(instance, sel, Masks.liquid(), Patterns.single(Block.AIR), player)

    fun smooth(instance: Instance, sel: CuboidSelection, iterations: Int = 1, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, sel.volume.toInt())
        repeat(iterations) {
            for (x in sel.minX..sel.maxX) {
                for (z in sel.minZ..sel.maxZ) {
                    var totalY = 0
                    var count = 0
                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            val nx = x + dx
                            val nz = z + dz
                            for (y in sel.maxY downTo sel.minY) {
                                val block = session.getBlock(nx, y, nz)
                                if (block.isSolid) {
                                    totalY += y
                                    count++
                                    break
                                }
                            }
                        }
                    }
                    if (count == 0) continue
                    val avgY = (totalY.toDouble() / count).roundToInt()
                    for (y in sel.minY..sel.maxY) {
                        if (y <= avgY) {
                            val current = session.getBlock(x, y, z)
                            if (current.isAir) session.setBlock(x, y, z, Block.STONE)
                        } else {
                            val current = session.getBlock(x, y, z)
                            if (current.isSolid) session.setBlock(x, y, z, Block.AIR)
                        }
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun naturalize(instance: Instance, sel: CuboidSelection, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, sel.volume.toInt())
        for (x in sel.minX..sel.maxX) {
            for (z in sel.minZ..sel.maxZ) {
                var depth = 0
                for (y in sel.maxY downTo sel.minY) {
                    val block = instance.getBlock(x, y, z)
                    if (block.isSolid) {
                        val replacement = when (depth) {
                            0 -> Block.GRASS_BLOCK
                            in 1..3 -> Block.DIRT
                            else -> Block.STONE
                        }
                        session.setBlock(x, y, z, replacement)
                        depth++
                    } else {
                        depth = 0
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun sphere(instance: Instance, center: Pos, radius: Double, pattern: Pattern, hollow: Boolean = false, player: Player? = null): Pair<EditResult, ChangeSet> {
        val r = radius.roundToInt()
        val session = EditSession(instance, player, ((4.0 / 3.0) * Math.PI * r * r * r).toInt())
        val cx = center.blockX()
        val cy = center.blockY()
        val cz = center.blockZ()
        val r2 = radius * radius
        val inner2 = if (hollow) (radius - 1) * (radius - 1) else -1.0
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
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun cylinder(instance: Instance, center: Pos, radius: Double, height: Int, pattern: Pattern, hollow: Boolean = false, player: Player? = null): Pair<EditResult, ChangeSet> {
        val r = radius.roundToInt()
        val session = EditSession(instance, player, (Math.PI * r * r * height).toInt())
        val cx = center.blockX()
        val cy = center.blockY()
        val cz = center.blockZ()
        val r2 = radius * radius
        val inner2 = if (hollow) (radius - 1) * (radius - 1) else -1.0
        for (y in 0 until height) {
            for (z in -r..r) {
                for (x in -r..r) {
                    val dist2 = (x * x + z * z).toDouble()
                    if (dist2 <= r2 && (!hollow || dist2 >= inner2)) {
                        session.setBlock(cx + x, cy + y, cz + z, pattern)
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun pyramid(instance: Instance, center: Pos, size: Int, pattern: Pattern, hollow: Boolean = false, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, size * size * size)
        val cx = center.blockX()
        val cy = center.blockY()
        val cz = center.blockZ()
        for (y in 0 until size) {
            val r = size - y - 1
            for (z in -r..r) {
                for (x in -r..r) {
                    if (!hollow || x == -r || x == r || z == -r || z == r || y == 0) {
                        session.setBlock(cx + x, cy + y, cz + z, pattern)
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun line(instance: Instance, from: Pos, to: Pos, pattern: Pattern, thickness: Int = 1, player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player)
        val dx = to.blockX() - from.blockX()
        val dy = to.blockY() - from.blockY()
        val dz = to.blockZ() - from.blockZ()
        val length = sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toInt().coerceAtLeast(1)

        for (i in 0..length) {
            val t = i.toDouble() / length
            val x = (from.blockX() + dx * t).roundToInt()
            val y = (from.blockY() + dy * t).roundToInt()
            val z = (from.blockZ() + dz * t).roundToInt()
            if (thickness <= 1) {
                session.setBlock(x, y, z, pattern)
            } else {
                val r = thickness / 2
                for (ox in -r..r) {
                    for (oy in -r..r) {
                        for (oz in -r..r) {
                            if (ox * ox + oy * oy + oz * oz <= r * r) {
                                session.setBlock(x + ox, y + oy, z + oz, pattern)
                            }
                        }
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }

    fun hollow(instance: Instance, sel: CuboidSelection, thickness: Int = 1, pattern: Pattern = Patterns.single(Block.AIR), player: Player? = null): Pair<EditResult, ChangeSet> {
        val session = EditSession(instance, player, sel.volume.toInt())
        for (y in sel.minY..sel.maxY) {
            for (z in sel.minZ..sel.maxZ) {
                for (x in sel.minX..sel.maxX) {
                    val block = instance.getBlock(x, y, z)
                    if (block.isAir) continue
                    val distX = min(x - sel.minX, sel.maxX - x)
                    val distY = min(y - sel.minY, sel.maxY - y)
                    val distZ = min(z - sel.minZ, sel.maxZ - z)
                    if (distX >= thickness && distY >= thickness && distZ >= thickness) {
                        session.setBlock(x, y, z, pattern)
                    }
                }
            }
        }
        val result = session.commit().join()
        return result to session.changeSet()
    }
}
