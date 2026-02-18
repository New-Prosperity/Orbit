package me.nebula.orbit.utils.pathfinding

import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Vec
import net.minestom.server.instance.Instance
import java.util.PriorityQueue

data class PathNode(
    val position: Vec,
    val gCost: Double,
    val hCost: Double,
    val parent: PathNode?,
) : Comparable<PathNode> {
    val fCost: Double get() = gCost + hCost
    override fun compareTo(other: PathNode): Int = fCost.compareTo(other.fCost)
}

object Pathfinder {

    fun findPath(
        instance: Instance,
        start: Point,
        end: Point,
        maxIterations: Int = 1000,
        maxDistance: Double = 64.0,
    ): List<Vec>? {
        val startVec = Vec(start.blockX().toDouble(), start.blockY().toDouble(), start.blockZ().toDouble())
        val endVec = Vec(end.blockX().toDouble(), end.blockY().toDouble(), end.blockZ().toDouble())

        if (startVec.distance(endVec) > maxDistance) return null

        val openSet = PriorityQueue<PathNode>()
        val closedSet = HashSet<Long>()

        openSet.add(PathNode(startVec, 0.0, heuristic(startVec, endVec), null))

        var iterations = 0
        while (openSet.isNotEmpty() && iterations < maxIterations) {
            iterations++
            val current = openSet.poll()

            if (current.position.blockX() == endVec.blockX() &&
                current.position.blockY() == endVec.blockY() &&
                current.position.blockZ() == endVec.blockZ()
            ) {
                return reconstructPath(current)
            }

            val key = posKey(current.position)
            if (!closedSet.add(key)) continue

            for (neighbor in getNeighbors(instance, current.position)) {
                val neighborKey = posKey(neighbor)
                if (neighborKey in closedSet) continue

                val gCost = current.gCost + current.position.distance(neighbor)
                val hCost = heuristic(neighbor, endVec)
                openSet.add(PathNode(neighbor, gCost, hCost, current))
            }
        }
        return null
    }

    private fun getNeighbors(instance: Instance, pos: Vec): List<Vec> {
        val neighbors = mutableListOf<Vec>()
        val offsets = listOf(
            Vec(1.0, 0.0, 0.0), Vec(-1.0, 0.0, 0.0),
            Vec(0.0, 0.0, 1.0), Vec(0.0, 0.0, -1.0),
            Vec(1.0, 1.0, 0.0), Vec(-1.0, 1.0, 0.0),
            Vec(0.0, 1.0, 1.0), Vec(0.0, 1.0, -1.0),
            Vec(1.0, -1.0, 0.0), Vec(-1.0, -1.0, 0.0),
            Vec(0.0, -1.0, 1.0), Vec(0.0, -1.0, -1.0),
        )

        for (offset in offsets) {
            val neighbor = pos.add(offset)
            val block = instance.getBlock(neighbor)
            val above = instance.getBlock(neighbor.add(0.0, 1.0, 0.0))
            val below = instance.getBlock(neighbor.add(0.0, -1.0, 0.0))

            if (!block.isSolid && !above.isSolid && below.isSolid) {
                neighbors.add(neighbor)
            }
        }
        return neighbors
    }

    private fun heuristic(a: Vec, b: Vec): Double =
        Math.abs(a.x() - b.x()) + Math.abs(a.y() - b.y()) + Math.abs(a.z() - b.z())

    private fun posKey(pos: Vec): Long {
        val x = pos.blockX().toLong()
        val y = pos.blockY().toLong()
        val z = pos.blockZ().toLong()
        return (x and 0x3FFFFFF shl 38) or (z and 0x3FFFFFF shl 12) or (y and 0xFFF)
    }

    private fun reconstructPath(node: PathNode): List<Vec> {
        val path = mutableListOf<Vec>()
        var current: PathNode? = node
        while (current != null) {
            path.add(current.position)
            current = current.parent
        }
        return path.reversed()
    }
}
