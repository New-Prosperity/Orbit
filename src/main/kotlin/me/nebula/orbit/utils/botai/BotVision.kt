package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

private const val VIEW_DISTANCE = 16
private const val FOV_RADIANS = Math.PI * 2.0 / 3.0
private const val HORIZONTAL_RAYS = 12
private const val DOWN_RAYS = 6
private const val UP_RAYS = 3
private const val PERIPHERAL_RAYS = 3
private const val SCAN_INTERVAL_TICKS = 20
private const val ENTITY_VIEW_DISTANCE_SQ = VIEW_DISTANCE.toDouble() * VIEW_DISTANCE.toDouble()
private const val PLAYER_EYE_HEIGHT = 1.62

private val COS_35 = cos(Math.toRadians(35.0))
private val SIN_35 = sin(Math.toRadians(35.0))
private val COS_40 = cos(Math.toRadians(40.0))
private val SIN_40 = sin(Math.toRadians(40.0))

private val TRANSPARENT_NAMES = hashSetOf(
    "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
    "minecraft:short_grass", "minecraft:tall_grass", "minecraft:fern", "minecraft:large_fern",
    "minecraft:dead_bush", "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
    "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip",
    "minecraft:white_tulip", "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower",
    "minecraft:lily_of_the_valley", "minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch",
    "minecraft:soul_wall_torch", "minecraft:snow", "minecraft:vine",
    "minecraft:water", "minecraft:lava",
)

private val INTERESTING_NAMES = hashSetOf(
    "minecraft:coal_ore", "minecraft:deepslate_coal_ore",
    "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
    "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
    "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
    "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
    "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
    "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore",
    "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
    "minecraft:oak_log", "minecraft:birch_log", "minecraft:spruce_log",
    "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
    "minecraft:mangrove_log", "minecraft:cherry_log",
    "minecraft:crafting_table", "minecraft:furnace",
    "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
)

data class VisibleBlock(val pos: Point, val block: Block, val distanceSq: Double)

class BotVision(private val player: Player) {

    private var scanCounter = ThreadLocalRandom.current().nextInt(SCAN_INTERVAL_TICKS)
    private val visibleBlocks = ArrayList<VisibleBlock>(32)
    private val seenPositions = HashSet<Long>(256)

    fun scan(instance: Instance) {
        if (++scanCounter % SCAN_INTERVAL_TICKS != 0) return

        visibleBlocks.clear()
        seenPositions.clear()

        val eyeX = player.position.x()
        val eyeY = player.position.y() + PLAYER_EYE_HEIGHT
        val eyeZ = player.position.z()
        val yawRad = Math.toRadians(-player.position.yaw().toDouble())

        castHorizontalFan(instance, eyeX, eyeY, eyeZ, yawRad)
        castDownwardRays(instance, eyeX, eyeY, eyeZ, yawRad)
        castUpwardRays(instance, eyeX, eyeY, eyeZ, yawRad)
        castPeripheralRays(instance, eyeX, eyeY, eyeZ)
    }

    fun canSee(block: Block): VisibleBlock? {
        var best: VisibleBlock? = null
        for (vb in visibleBlocks) {
            if (vb.block.compare(block) && (best == null || vb.distanceSq < best.distanceSq)) {
                best = vb
            }
        }
        return best
    }

    fun canSeeAny(vararg blocks: Block): VisibleBlock? {
        var best: VisibleBlock? = null
        for (vb in visibleBlocks) {
            for (b in blocks) {
                if (vb.block.compare(b)) {
                    if (best == null || vb.distanceSq < best.distanceSq) best = vb
                    break
                }
            }
        }
        return best
    }

    fun visibleOres(): List<VisibleBlock> {
        val result = ArrayList<VisibleBlock>(8)
        for (vb in visibleBlocks) {
            if (MiningKnowledge.isOre(vb.block)) result.add(vb)
        }
        return result
    }

    fun canSeePlayer(target: Player): Boolean {
        val instance = player.instance ?: return false
        if (target.position.distanceSquared(player.position) > ENTITY_VIEW_DISTANCE_SQ) return false
        val eyePos = player.position.add(0.0, PLAYER_EYE_HEIGHT, 0.0)
        return hasLineOfSight(instance, eyePos, target.position.add(0.0, target.eyeHeight, 0.0))
    }

    fun seesOpenSpace(direction: Vec): Boolean {
        val instance = player.instance ?: return false
        val startX = player.position.x()
        val startY = player.position.y() + PLAYER_EYE_HEIGHT
        val startZ = player.position.z()
        val dx = direction.x()
        val dz = direction.z()
        var airCount = 0
        for (step in 1..8) {
            val block = instance.getBlock(
                (startX + dx * step).toInt(),
                (startY).toInt(),
                (startZ + dz * step).toInt(),
            )
            if (block.isAir) airCount++ else return airCount >= 3
        }
        return airCount >= 3
    }

    fun allVisible(): List<VisibleBlock> = visibleBlocks

    fun visibleOfType(block: Block): List<VisibleBlock> {
        val result = ArrayList<VisibleBlock>(4)
        for (vb in visibleBlocks) {
            if (vb.block.compare(block)) result.add(vb)
        }
        return result
    }

    private fun castHorizontalFan(instance: Instance, eyeX: Double, eyeY: Double, eyeZ: Double, yawRad: Double) {
        val halfFov = FOV_RADIANS / 2.0
        val step = FOV_RADIANS / (HORIZONTAL_RAYS - 1)
        var angle = yawRad - halfFov
        for (i in 0 until HORIZONTAL_RAYS) {
            castRay(instance, eyeX, eyeY, eyeZ, -sin(angle), 0.0, cos(angle))
            angle += step
        }
    }

    private fun castDownwardRays(instance: Instance, eyeX: Double, eyeY: Double, eyeZ: Double, yawRad: Double) {
        val halfFov = FOV_RADIANS / 2.0
        val step = FOV_RADIANS / (DOWN_RAYS - 1).coerceAtLeast(1)
        var angle = yawRad - halfFov
        for (i in 0 until DOWN_RAYS) {
            castRay(instance, eyeX, eyeY, eyeZ, -sin(angle) * COS_35, -SIN_35, cos(angle) * COS_35)
            angle += step
        }
    }

    private fun castUpwardRays(instance: Instance, eyeX: Double, eyeY: Double, eyeZ: Double, yawRad: Double) {
        val halfFov = FOV_RADIANS / 2.0
        val step = FOV_RADIANS / (UP_RAYS - 1).coerceAtLeast(1)
        var angle = yawRad - halfFov
        for (i in 0 until UP_RAYS) {
            castRay(instance, eyeX, eyeY, eyeZ, -sin(angle) * COS_40, SIN_40, cos(angle) * COS_40)
            angle += step
        }
    }

    private fun castPeripheralRays(instance: Instance, eyeX: Double, eyeY: Double, eyeZ: Double) {
        val rng = ThreadLocalRandom.current()
        for (i in 0 until PERIPHERAL_RAYS) {
            val yaw = rng.nextDouble(-Math.PI, Math.PI)
            val pitch = rng.nextDouble(-Math.PI / 3.0, Math.PI / 3.0)
            val cp = cos(pitch)
            castRay(instance, eyeX, eyeY, eyeZ, -sin(yaw) * cp, sin(pitch), cos(yaw) * cp)
        }
    }

    private fun castRay(
        instance: Instance,
        startX: Double,
        startY: Double,
        startZ: Double,
        dx: Double,
        dy: Double,
        dz: Double,
    ) {
        for (step in 1..VIEW_DISTANCE) {
            val x = (startX + dx * step).toInt()
            val y = (startY + dy * step).toInt()
            val z = (startZ + dz * step).toInt()
            val posKey = (x.toLong() and 0x3FFFFFF shl 38) or (z.toLong() and 0x3FFFFFF shl 12) or (y.toLong() and 0xFFF)
            if (!seenPositions.add(posKey)) continue

            val block = instance.getBlock(x, y, z)
            val name = block.name()

            if (name in INTERESTING_NAMES) {
                val bx = x - startX
                val by = y - startY
                val bz = z - startZ
                visibleBlocks.add(VisibleBlock(Vec(x + 0.5, y.toDouble(), z + 0.5), block, bx * bx + by * by + bz * bz))
            }

            if (!block.isAir && name !in TRANSPARENT_NAMES) return
        }
    }

    private fun hasLineOfSight(instance: Instance, from: Point, to: Point): Boolean {
        val dx = to.x() - from.x()
        val dy = to.y() - from.y()
        val dz = to.z() - from.z()
        val distSq = dx * dx + dy * dy + dz * dz
        if (distSq < 0.25) return true
        val steps = kotlin.math.sqrt(distSq).toInt().coerceAtLeast(1)
        val stepX = dx / steps
        val stepY = dy / steps
        val stepZ = dz / steps
        for (i in 1 until steps) {
            val block = instance.getBlock(
                (from.x() + stepX * i).toInt(),
                (from.y() + stepY * i).toInt(),
                (from.z() + stepZ * i).toInt(),
            )
            if (!block.isAir && block.name() !in TRANSPARENT_NAMES) return false
        }
        return true
    }
}
