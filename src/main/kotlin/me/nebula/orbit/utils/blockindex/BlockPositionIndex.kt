package me.nebula.orbit.utils.blockindex

import net.minestom.server.coordinate.Vec
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import java.util.concurrent.ConcurrentHashMap

class BlockPositionIndex(
    private val targetBlockNames: Set<String>,
    private val eventNode: EventNode<Event>,
) {

    val instancePositions = ConcurrentHashMap<Int, MutableSet<Long>>()

    fun install(): BlockPositionIndex {
        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() in targetBlockNames) {
                val pos = event.blockPosition
                val hash = System.identityHashCode(event.player.instance)
                positions(hash).add(pack(pos.blockX(), pos.blockY(), pos.blockZ()))
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() in targetBlockNames) {
                val pos = event.blockPosition
                val hash = System.identityHashCode(event.player.instance)
                positions(hash).remove(pack(pos.blockX(), pos.blockY(), pos.blockZ()))
            }
        }

        return this
    }

    fun positionsNear(instance: Instance, center: Vec, radius: Double): List<Vec> {
        val hash = System.identityHashCode(instance)
        val set = instancePositions[hash] ?: return emptyList()
        val radiusSq = radius * radius
        val cx = center.x()
        val cy = center.y()
        val cz = center.z()
        val result = mutableListOf<Vec>()

        for (packed in set) {
            val (x, y, z) = unpack(packed)
            val dx = x + 0.5 - cx
            val dy = y + 0.5 - cy
            val dz = z + 0.5 - cz
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                result.add(Vec(x.toDouble(), y.toDouble(), z.toDouble()))
            }
        }
        return result
    }

    fun allPositions(instance: Instance): Set<Long> =
        instancePositions[System.identityHashCode(instance)] ?: emptySet()

    fun scanChunk(instance: Instance, chunk: net.minestom.server.instance.Chunk, minY: Int = -64, maxY: Int = 320) {
        val hash = System.identityHashCode(instance)
        val set = positions(hash)
        val minX = chunk.chunkX shl 4
        val minZ = chunk.chunkZ shl 4

        for (x in minX until minX + 16) {
            for (z in minZ until minZ + 16) {
                for (y in minY until maxY) {
                    val block = chunk.getBlock(x, y, z)
                    if (block.name() in targetBlockNames) {
                        set.add(pack(x, y, z))
                    }
                }
            }
        }
    }

    fun evictInstance(instanceHash: Int) {
        instancePositions.remove(instanceHash)
    }

    fun clear() {
        instancePositions.clear()
    }

    private fun positions(hash: Int): MutableSet<Long> =
        instancePositions.computeIfAbsent(hash) { ConcurrentHashMap.newKeySet() }

    companion object {
        fun pack(x: Int, y: Int, z: Int): Long =
            (x.toLong() shl 40) or ((y.toLong() and 0xFFFFF) shl 20) or (z.toLong() and 0xFFFFF)

        fun unpack(packed: Long): Triple<Int, Int, Int> {
            val x = (packed shr 40).toInt()
            val yRaw = ((packed shr 20) and 0xFFFFF).toInt()
            val y = if (yRaw >= 0x80000) yRaw - 0x100000 else yRaw
            val zRaw = (packed and 0xFFFFF).toInt()
            val z = if (zRaw >= 0x80000) zRaw - 0x100000 else zRaw
            return Triple(x, y, z)
        }
    }
}
