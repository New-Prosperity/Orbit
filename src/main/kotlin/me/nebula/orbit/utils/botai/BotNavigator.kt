package me.nebula.orbit.utils.botai

import me.nebula.orbit.utils.vanilla.packBlockPos
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

private const val WAYPOINT_REACH_THRESHOLD = 0.5
private const val SPRINT_THRESHOLD = 8.0
private const val STUCK_TICKS = 20
private const val STUCK_DISTANCE_SQ = 0.04
private const val MAX_DROP_HEIGHT = 3
private const val WATER_SPEED_MULTIPLIER = 0.6

private val PASSABLE_BLOCKS = setOf(
    Block.AIR, Block.CAVE_AIR, Block.VOID_AIR,
    Block.SHORT_GRASS, Block.TALL_GRASS, Block.FERN, Block.LARGE_FERN,
    Block.DEAD_BUSH, Block.DANDELION, Block.POPPY, Block.BLUE_ORCHID,
    Block.ALLIUM, Block.AZURE_BLUET, Block.RED_TULIP, Block.ORANGE_TULIP,
    Block.WHITE_TULIP, Block.PINK_TULIP, Block.OXEYE_DAISY, Block.CORNFLOWER,
    Block.LILY_OF_THE_VALLEY, Block.TORCH, Block.WALL_TORCH, Block.SOUL_TORCH,
    Block.SOUL_WALL_TORCH, Block.REDSTONE_TORCH, Block.REDSTONE_WALL_TORCH,
    Block.SNOW, Block.VINE, Block.RAIL, Block.POWERED_RAIL, Block.DETECTOR_RAIL,
    Block.ACTIVATOR_RAIL, Block.COBWEB, Block.SUGAR_CANE, Block.WHEAT,
    Block.CARROTS, Block.POTATOES, Block.BEETROOTS, Block.SWEET_BERRY_BUSH,
    Block.OAK_SIGN, Block.OAK_WALL_SIGN, Block.OAK_HANGING_SIGN, Block.OAK_WALL_HANGING_SIGN,
    Block.LEVER, Block.STONE_BUTTON, Block.TRIPWIRE, Block.TRIPWIRE_HOOK,
    Block.REDSTONE_WIRE, Block.REPEATER, Block.COMPARATOR,
    Block.MOSS_CARPET, Block.PINK_PETALS, Block.FIRE, Block.SOUL_FIRE,
)

private val WATER_BLOCKS = setOf(Block.WATER, Block.BUBBLE_COLUMN)

private fun isPassable(instance: Instance, x: Int, y: Int, z: Int): Boolean {
    val block = instance.getBlock(x, y, z)
    if (block.isAir || block.isLiquid) return true
    return PASSABLE_BLOCKS.any { block.compare(it) }
}

private fun isWater(instance: Instance, x: Int, y: Int, z: Int): Boolean {
    val block = instance.getBlock(x, y, z)
    return WATER_BLOCKS.any { block.compare(it) }
}

private fun isSolidSupport(instance: Instance, x: Int, y: Int, z: Int): Boolean {
    val block = instance.getBlock(x, y, z)
    return block.isSolid && !block.isLiquid
}

private fun isWalkable(instance: Instance, x: Int, y: Int, z: Int): Boolean {
    val feetPassable = isPassable(instance, x, y, z)
    val headPassable = isPassable(instance, x, y + 1, z)
    val groundSolid = isSolidSupport(instance, x, y - 1, z) || isWater(instance, x, y - 1, z)
    return feetPassable && headPassable && groundSolid
}

private fun isInWater(instance: Instance, x: Int, y: Int, z: Int): Boolean =
    isWater(instance, x, y, z) || isWater(instance, x, y - 1, z)

private fun manhattanDistance(x1: Int, y1: Int, z1: Int, x2: Int, y2: Int, z2: Int): Int =
    abs(x1 - x2) + abs(y1 - y2) + abs(z1 - z2)

private data class PathNode(
    val x: Int,
    val y: Int,
    val z: Int,
    val g: Int,
    val f: Int,
    val parentKey: Long,
    val jump: Boolean,
)

object BotPathfinder {

    private val DX = intArrayOf(1, -1, 0, 0, 1, -1, 1, -1)
    private val DZ = intArrayOf(0, 0, 1, -1, 1, 1, -1, -1)

    fun findPath(
        instance: Instance,
        start: Point,
        end: Point,
        maxIterations: Int = 500,
        maxDistance: Double = 64.0,
    ): List<Pos>? {
        val sx = floor(start.x()).toInt()
        val sy = floor(start.y()).toInt()
        val sz = floor(start.z()).toInt()
        val ex = floor(end.x()).toInt()
        val ey = floor(end.y()).toInt()
        val ez = floor(end.z()).toInt()

        if (start.distance(end) > maxDistance) return null

        val open = PriorityQueue<PathNode>(256, compareBy { it.f })
        val gScores = HashMap<Long, Int>(256)
        val parents = HashMap<Long, Long>(256)
        val nodePositions = HashMap<Long, Triple<Int, Int, Int>>(256)

        val startKey = packBlockPos(sx, sy, sz)
        val startH = manhattanDistance(sx, sy, sz, ex, ey, ez)
        open.add(PathNode(sx, sy, sz, 0, startH, -1L, false))
        gScores[startKey] = 0
        nodePositions[startKey] = Triple(sx, sy, sz)

        var iterations = 0
        while (open.isNotEmpty() && iterations < maxIterations) {
            iterations++
            val current = open.poll()
            val cx = current.x
            val cy = current.y
            val cz = current.z
            val currentKey = packBlockPos(cx, cy, cz)

            if (cx == ex && cy == ey && cz == ez) {
                return reconstructPath(currentKey, parents, nodePositions)
            }

            val currentG = current.g

            for (i in DX.indices) {
                val nx = cx + DX[i]
                val nz = cz + DZ[i]

                if (isWalkable(instance, nx, cy, nz)) {
                    val isDiagonal = DX[i] != 0 && DZ[i] != 0
                    val moveCost = if (isDiagonal) 14 else 10
                    val waterCost = if (isInWater(instance, nx, cy, nz)) 5 else 0
                    val newG = currentG + moveCost + waterCost
                    val neighborKey = packBlockPos(nx, cy, nz)
                    val existingG = gScores[neighborKey]

                    if (existingG == null || newG < existingG) {
                        gScores[neighborKey] = newG
                        parents[neighborKey] = currentKey
                        nodePositions[neighborKey] = Triple(nx, cy, nz)
                        val h = manhattanDistance(nx, cy, nz, ex, ey, ez)
                        open.add(PathNode(nx, cy, nz, newG, newG + h, currentKey, false))
                    }
                    continue
                }

                if (isPassable(instance, nx, cy + 1, nz) &&
                    isPassable(instance, nx, cy + 2, nz) &&
                    isSolidSupport(instance, cx, cy, cz)
                ) {
                    val newG = currentG + 20
                    val neighborKey = packBlockPos(nx, cy + 1, nz)
                    val existingG = gScores[neighborKey]

                    if (existingG == null || newG < existingG) {
                        gScores[neighborKey] = newG
                        parents[neighborKey] = currentKey
                        nodePositions[neighborKey] = Triple(nx, cy + 1, nz)
                        val h = manhattanDistance(nx, cy + 1, nz, ex, ey, ez)
                        open.add(PathNode(nx, cy + 1, nz, newG, newG + h, currentKey, true))
                    }
                    continue
                }

                for (drop in 1..MAX_DROP_HEIGHT) {
                    val dropY = cy - drop
                    if (dropY < -64) break

                    if (isWalkable(instance, nx, dropY, nz)) {
                        val newG = currentG + 10 + drop * 4
                        val neighborKey = packBlockPos(nx, dropY, nz)
                        val existingG = gScores[neighborKey]

                        if (existingG == null || newG < existingG) {
                            gScores[neighborKey] = newG
                            parents[neighborKey] = currentKey
                            nodePositions[neighborKey] = Triple(nx, dropY, nz)
                            val h = manhattanDistance(nx, dropY, nz, ex, ey, ez)
                            open.add(PathNode(nx, dropY, nz, newG, newG + h, currentKey, false))
                        }
                        break
                    }

                    if (!isPassable(instance, nx, dropY, nz)) break
                }
            }
        }

        return null
    }

    private fun reconstructPath(
        endKey: Long,
        parents: HashMap<Long, Long>,
        nodePositions: HashMap<Long, Triple<Int, Int, Int>>,
    ): List<Pos> {
        val keys = mutableListOf(endKey)
        var current = endKey
        while (true) {
            val parent = parents[current] ?: break
            keys.add(parent)
            current = parent
        }
        keys.reverse()

        return keys.mapNotNull { key ->
            val (x, y, z) = nodePositions[key] ?: return@mapNotNull null
            Pos(x + 0.5, y.toDouble(), z + 0.5)
        }
    }
}

private const val REPATH_COOLDOWN_TICKS = 40
private const val MAX_REPATH_FAILURES = 3

class BotPathFollower(private val player: Player) {

    var currentPath: List<Pos>? = null
        private set
    var currentIndex: Int = 0
        private set
    val isNavigating: Boolean get() = currentPath != null || pathfinding
    val isComplete: Boolean get() = !pathfinding && (currentPath?.let { currentIndex >= it.size } ?: true)

    private var lastPos: Pos = Pos.ZERO
    private var stuckTicks: Int = 0
    private var navigationTarget: Point? = null
    private var repathCooldown = 0
    private var repathFailures = 0
    @Volatile private var pendingPath: List<Pos>? = null
    @Volatile private var pathfinding = false

    fun navigateTo(instance: Instance, target: Point): Boolean {
        if (pathfinding) return false
        val prevTarget = navigationTarget
        if (prevTarget == null || target.distanceSquared(prevTarget) > 4.0) {
            repathFailures = 0
            repathCooldown = 0
        }
        pathfinding = true
        navigationTarget = target
        val startPos = player.position
        Thread.startVirtualThread {
            val path = BotPathfinder.findPath(instance, startPos, target)
            if (path != null && path.size >= 2) {
                pendingPath = path
            }
            pathfinding = false
        }
        return true
    }

    fun tick() {
        val pending = pendingPath
        if (pending != null) {
            currentPath = pending
            currentIndex = 1
            lastPos = player.position
            stuckTicks = 0
            pendingPath = null
        }

        val path = currentPath ?: return
        if (currentIndex >= path.size) {
            stop()
            return
        }

        val waypoint = path[currentIndex]
        val playerPos = player.position
        val dx = waypoint.x() - playerPos.x()
        val dz = waypoint.z() - playerPos.z()
        val horizontalDistSq = dx * dx + dz * dz

        if (horizontalDistSq < WAYPOINT_REACH_THRESHOLD * WAYPOINT_REACH_THRESHOLD) {
            currentIndex++
            if (currentIndex >= path.size) {
                stop()
                return
            }
            stuckTicks = 0
            return
        }

        val horizontalDist = sqrt(horizontalDistSq)
        val normX = dx / horizontalDist
        val normZ = dz / horizontalDist

        val target = navigationTarget
        val totalDist = if (target != null) playerPos.distance(target) else horizontalDist
        val shouldSprint = totalDist > SPRINT_THRESHOLD
        player.isSprinting = shouldSprint
        val speed = if (shouldSprint) BotMovement.SPRINT_SPEED else BotMovement.WALK_SPEED

        val instance = player.instance
        val inWater = instance != null && isInWater(
            instance,
            floor(playerPos.x()).toInt(),
            floor(playerPos.y()).toInt(),
            floor(playerPos.z()).toInt(),
        )
        val effectiveSpeed = if (inWater) speed * WATER_SPEED_MULTIPLIER else speed

        val velX = normX * effectiveSpeed * BotMovement.VELOCITY_SCALE
        val velZ = normZ * effectiveSpeed * BotMovement.VELOCITY_SCALE
        var velY = player.velocity.y()

        val dy = waypoint.y() - playerPos.y()
        if (dy > 0.4 && player.isOnGround) {
            velY = BotMovement.JUMP_IMPULSE
        }

        if (instance != null && player.isOnGround) {
            val checkX = floor(playerPos.x() + normX * 0.8).toInt()
            val checkY = floor(playerPos.y()).toInt()
            val checkZ = floor(playerPos.z() + normZ * 0.8).toInt()
            val blockAhead = instance.getBlock(checkX, checkY, checkZ)
            if (blockAhead.isSolid && dy <= 0.0) {
                velY = BotMovement.JUMP_IMPULSE
            }
        }

        player.velocity = Vec(velX, velY, velZ)

        val yaw = -Math.toDegrees(atan2(normX, normZ)).toFloat()
        val pitch = -Math.toDegrees(atan2(dy, horizontalDist)).toFloat().coerceIn(-45f, 45f)
        player.setView(yaw, pitch)

        if (repathCooldown > 0) repathCooldown--

        val movedDistSq = playerPos.distanceSquared(lastPos)
        if (movedDistSq < STUCK_DISTANCE_SQ) {
            stuckTicks++
            if (stuckTicks >= STUCK_TICKS) {
                if (repathCooldown > 0) {
                    lastPos = playerPos
                    return
                }
                if (repathFailures >= MAX_REPATH_FAILURES) {
                    stop()
                    return
                }
                repathCooldown = REPATH_COOLDOWN_TICKS
                repathFailures++
                val navTarget = navigationTarget
                if (navTarget != null && instance != null) {
                    repath(instance, navTarget)
                } else {
                    stop()
                }
            }
        } else {
            stuckTicks = 0
        }
        lastPos = playerPos
    }

    fun stop() {
        currentPath = null
        currentIndex = 0
        navigationTarget = null
        stuckTicks = 0
        repathCooldown = 0
        repathFailures = 0
        player.isSprinting = false
        player.velocity = Vec(0.0, player.velocity.y(), 0.0)
    }

    fun repath(instance: Instance, target: Point) {
        if (pathfinding) return
        stuckTicks = 0
        navigationTarget = target
        pathfinding = true
        val startPos = player.position
        Thread.startVirtualThread {
            val path = BotPathfinder.findPath(instance, startPos, target)
            if (path != null && path.size >= 2) {
                pendingPath = path
            } else {
                currentPath = null
                navigationTarget = null
            }
            pathfinding = false
        }
    }
}
