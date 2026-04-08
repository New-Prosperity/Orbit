package me.nebula.orbit.utils.botai

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.cos
import kotlin.math.sin

private const val DIRECTION_CHANGE_MIN_TICKS = 200
private const val DIRECTION_CHANGE_MAX_TICKS = 400
private const val EXPLORATION_AHEAD_DISTANCE = 20.0
private const val CAVE_Y_THRESHOLD = 55

sealed interface ExplorationInterest {
    val pos: Point
    val priority: Float

    data class VisibleOre(override val pos: Point, val block: Block, override val priority: Float) : ExplorationInterest
    data class CaveOpening(override val pos: Point, override val priority: Float) : ExplorationInterest
    data class Tree(override val pos: Point, override val priority: Float) : ExplorationInterest
    data class Container(override val pos: Point, override val priority: Float) : ExplorationInterest
    data class OpenArea(override val pos: Point, override val priority: Float) : ExplorationInterest
    data class UnexploredDirection(override val pos: Point, override val priority: Float) : ExplorationInterest
    data class EnemyPlayer(override val pos: Point, val player: Player, override val priority: Float) : ExplorationInterest
    data class DroppedItem(override val pos: Point, override val priority: Float) : ExplorationInterest
}

private val CONTAINER_BLOCKS = setOf(
    Block.CHEST, Block.TRAPPED_CHEST, Block.BARREL,
)

class BotExploration(
    private val brain: BotBrain,
    private val vision: BotVision,
) {

    companion object {
        private val CARDINAL_DIRECTIONS = listOf(
            Vec(1.0, 0.0, 0.0), Vec(-1.0, 0.0, 0.0),
            Vec(0.0, 0.0, 1.0), Vec(0.0, 0.0, -1.0),
            Vec(0.707, 0.0, 0.707), Vec(-0.707, 0.0, 0.707),
            Vec(0.707, 0.0, -0.707), Vec(-0.707, 0.0, -0.707),
        )
    }

    private var explorationDirection: Vec = randomHorizontalDirection()
    private var directionChangeTick = 0
    private var directionChangeTarget = randomDirectionInterval()
    private var tickCounter = 0

    fun findInterest(): ExplorationInterest? {
        val interests = ArrayList<ExplorationInterest>(8)

        for (vb in vision.allVisible()) {
            when {
                MiningKnowledge.isOre(vb.block) -> {
                    interests.add(ExplorationInterest.VisibleOre(vb.pos, vb.block, orePriority(vb.block)))
                    brain.memory.rememberResource(vb.block.name(), vb.pos)
                }
                isLogBlock(vb.block) -> {
                    interests.add(ExplorationInterest.Tree(vb.pos, 0.4f))
                    brain.memory.rememberResource("log", vb.pos)
                }
                isContainer(vb.block) -> {
                    interests.add(ExplorationInterest.Container(vb.pos, 0.6f))
                    brain.memory.rememberLocation("container", vb.pos)
                }
                vb.block.compare(Block.CRAFTING_TABLE) -> {
                    brain.memory.rememberLocation("crafting_table", vb.pos)
                }
                vb.block.compare(Block.FURNACE) -> {
                    brain.memory.rememberLocation("furnace", vb.pos)
                }
            }
        }

        detectCaveOpenings(interests)

        val nearestEnemy = brain.findNearestPlayer(16.0)
        if (nearestEnemy != null && vision.canSeePlayer(nearestEnemy)) {
            val threatLevel = brain.memory.getThreatLevel(nearestEnemy.uuid)
            val basePriority = if (threatLevel > 0f) 0.7f else 0.3f
            val priority = basePriority * (brain.personality.aggression + 0.3f)
            interests.add(ExplorationInterest.EnemyPlayer(nearestEnemy.position, nearestEnemy, priority.coerceIn(0f, 1f)))
        }

        val nearbyItem = brain.findNearestItem(8.0)
        if (nearbyItem != null) {
            interests.add(ExplorationInterest.DroppedItem(nearbyItem.position, 0.4f))
        }

        if (interests.isEmpty()) {
            val ahead = aheadPosition()
            interests.add(ExplorationInterest.UnexploredDirection(ahead, 0.2f))
        }

        return interests.maxByOrNull { it.priority }
    }

    fun exploreDirection(): Vec {
        val instance = brain.player.instance ?: return explorationDirection
        val pos = brain.player.position

        if (pos.y() < CAVE_Y_THRESHOLD) {
            return followCave(instance)
        }

        val explored = brain.memory.recallLocations("explored")
        val candidates = CARDINAL_DIRECTIONS
        val rng = ThreadLocalRandom.current()

        var bestDir = explorationDirection
        var bestScore = -1.0

        for (dir in candidates) {
            val testPos = Vec(pos.x() + dir.x() * EXPLORATION_AHEAD_DISTANCE, pos.y(), pos.z() + dir.z() * EXPLORATION_AHEAD_DISTANCE)
            val exploredNearby = explored.count { it.distanceSquared(testPos) < 256.0 }
            val openSpace = if (vision.seesOpenSpace(dir)) 2.0 else 0.0
            val score = (10.0 - exploredNearby) + openSpace + rng.nextDouble(0.0, 2.0)
            if (score > bestScore) {
                bestScore = score
                bestDir = dir
            }
        }

        return bestDir
    }

    fun shouldChangeDirection(): Boolean {
        if (directionChangeTick >= directionChangeTarget) return true
        val instance = brain.player.instance ?: return false
        val pos = brain.player.position
        val aheadX = (pos.x() + explorationDirection.x() * 3).toInt()
        val aheadY = pos.blockY()
        val aheadZ = (pos.z() + explorationDirection.z() * 3).toInt()
        val blockAhead = instance.getBlock(aheadX, aheadY, aheadZ)
        val blockAbove = instance.getBlock(aheadX, aheadY + 1, aheadZ)
        return blockAhead.isSolid && blockAbove.isSolid
    }

    fun tick() {
        tickCounter++
        directionChangeTick++

        if (shouldChangeDirection()) {
            explorationDirection = exploreDirection()
            directionChangeTick = 0
            directionChangeTarget = randomDirectionInterval()
        }

        brain.memory.rememberLocation("explored", brain.player.position)
    }

    private fun followCave(instance: Instance): Vec {
        val pos = brain.player.position
        var bestDir = explorationDirection
        var bestAir = 0

        for (dir in CARDINAL_DIRECTIONS) {
            var airCount = 0
            for (step in 1..8) {
                val x = (pos.x() + dir.x() * step).toInt()
                val y = pos.blockY()
                val z = (pos.z() + dir.z() * step).toInt()
                val feetBlock = instance.getBlock(x, y, z)
                val headBlock = instance.getBlock(x, y + 1, z)
                if (feetBlock.isAir && headBlock.isAir) airCount++
            }
            if (airCount > bestAir) {
                bestAir = airCount
                bestDir = dir
            }
        }
        return bestDir
    }

    private fun detectCaveOpenings(interests: MutableList<ExplorationInterest>) {
        val instance = brain.player.instance ?: return
        val pos = brain.player.position
        if (pos.y() < CAVE_Y_THRESHOLD) return

        val directions = CARDINAL_DIRECTIONS
        for (dir in directions) {
            for (depth in 2..6) {
                val checkX = (pos.x() + dir.x() * depth).toInt()
                val checkZ = (pos.z() + dir.z() * depth).toInt()
                for (dy in -4..-1) {
                    val y = pos.blockY() + dy
                    val block = instance.getBlock(checkX, y, checkZ)
                    val above = instance.getBlock(checkX, y + 1, checkZ)
                    if (block.isAir && above.isAir) {
                        val hasStone = listOf(
                            instance.getBlock(checkX + 1, y, checkZ),
                            instance.getBlock(checkX - 1, y, checkZ),
                            instance.getBlock(checkX, y, checkZ + 1),
                            instance.getBlock(checkX, y, checkZ - 1),
                        ).any { it.compare(Block.STONE) || it.compare(Block.DEEPSLATE) }
                        if (hasStone) {
                            val cavePos = Vec(checkX + 0.5, y.toDouble(), checkZ + 0.5)
                            val explored = brain.memory.recallLocations("explored_cave")
                            if (explored.none { it.distanceSquared(cavePos) < 64.0 }) {
                                interests.add(ExplorationInterest.CaveOpening(cavePos, 0.5f))
                                return
                            }
                        }
                    }
                }
            }
        }
    }

    private fun aheadPosition(): Point {
        val pos = brain.player.position
        return Vec(
            pos.x() + explorationDirection.x() * EXPLORATION_AHEAD_DISTANCE,
            pos.y(),
            pos.z() + explorationDirection.z() * EXPLORATION_AHEAD_DISTANCE,
        )
    }

    private fun orePriority(block: Block): Float = when {
        block.compare(Block.DIAMOND_ORE) || block.compare(Block.DEEPSLATE_DIAMOND_ORE) -> 0.9f
        block.compare(Block.EMERALD_ORE) || block.compare(Block.DEEPSLATE_EMERALD_ORE) -> 0.85f
        block.compare(Block.GOLD_ORE) || block.compare(Block.DEEPSLATE_GOLD_ORE) -> 0.65f
        block.compare(Block.IRON_ORE) || block.compare(Block.DEEPSLATE_IRON_ORE) -> 0.6f
        block.compare(Block.LAPIS_ORE) || block.compare(Block.DEEPSLATE_LAPIS_ORE) -> 0.5f
        block.compare(Block.REDSTONE_ORE) || block.compare(Block.DEEPSLATE_REDSTONE_ORE) -> 0.45f
        block.compare(Block.COPPER_ORE) || block.compare(Block.DEEPSLATE_COPPER_ORE) -> 0.35f
        block.compare(Block.COAL_ORE) || block.compare(Block.DEEPSLATE_COAL_ORE) -> 0.3f
        else -> 0.3f
    }

    private fun isLogBlock(block: Block): Boolean = LOG_BLOCKS.any { block.compare(it) }

    private fun isContainer(block: Block): Boolean = CONTAINER_BLOCKS.any { block.compare(it) }
}

private fun randomHorizontalDirection(): Vec {
    val rng = ThreadLocalRandom.current()
    val angle = rng.nextDouble(0.0, Math.PI * 2.0)
    return Vec(cos(angle), 0.0, sin(angle))
}

private fun randomDirectionInterval(): Int =
    ThreadLocalRandom.current().nextInt(DIRECTION_CHANGE_MIN_TICKS, DIRECTION_CHANGE_MAX_TICKS)
