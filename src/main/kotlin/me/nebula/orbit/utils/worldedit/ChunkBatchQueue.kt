package me.nebula.orbit.utils.worldedit

import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

private const val BACKPRESSURE_THRESHOLD = 64
private const val MIN_SECTION = -4
private const val MAX_SECTION = 19
private const val SECTION_COUNT = MAX_SECTION - MIN_SECTION + 1

class ChunkBuffer(val chunkX: Int, val chunkZ: Int) {

    private val sections = arrayOfNulls<IntArray>(SECTION_COUNT)
    private val modified = BooleanArray(SECTION_COUNT)

    fun set(localX: Int, worldY: Int, localZ: Int, stateId: Int) {
        val sectionIndex = (worldY shr 4) - MIN_SECTION
        if (sectionIndex !in 0 until SECTION_COUNT) return

        var section = sections[sectionIndex]
        if (section == null) {
            section = IntArray(4096) { -1 }
            sections[sectionIndex] = section
        }

        val localY = worldY and 15
        section[(localY shl 8) or (localZ shl 4) or localX] = stateId
        modified[sectionIndex] = true
    }

    fun get(localX: Int, worldY: Int, localZ: Int): Int {
        val sectionIndex = (worldY shr 4) - MIN_SECTION
        if (sectionIndex !in 0 until SECTION_COUNT) return -1

        val section = sections[sectionIndex] ?: return -1
        val localY = worldY and 15
        return section[(localY shl 8) or (localZ shl 4) or localX]
    }

    fun applyTo(chunk: Chunk) {
        for (sectionIndex in 0 until SECTION_COUNT) {
            if (!modified[sectionIndex]) continue
            val data = sections[sectionIndex] ?: continue
            val section = chunk.getSection(MIN_SECTION + sectionIndex)
            val palette = section.blockPalette()
            for (idx in 0 until 4096) {
                val stateId = data[idx]
                if (stateId < 0) continue
                val y = idx shr 8
                val z = (idx shr 4) and 15
                val x = idx and 15
                palette.set(x, y, z, stateId)
            }
        }
        chunk.invalidate()
        chunk.sendChunk()
    }

    fun isModified(): Boolean = modified.any { it }
}

class ChunkBatchQueue(private val instance: Instance) {

    private val chunks = ConcurrentHashMap<Long, ChunkBuffer>()

    fun setBlock(x: Int, y: Int, z: Int, stateId: Int) {
        val chunkX = x shr 4
        val chunkZ = z shr 4
        val key = packKey(chunkX, chunkZ)
        val buffer = chunks.getOrPut(key) { ChunkBuffer(chunkX, chunkZ) }
        buffer.set(x and 15, y, z and 15, stateId)
    }

    fun getBuffered(x: Int, y: Int, z: Int): Int {
        val key = packKey(x shr 4, z shr 4)
        val buffer = chunks[key] ?: return -1
        return buffer.get(x and 15, y, z and 15)
    }

    fun flush(): CompletableFuture<Void> {
        val buffers = chunks.values.filter { it.isModified() }.toList()
        chunks.clear()

        if (buffers.isEmpty()) return CompletableFuture.completedFuture(null)

        val future = CompletableFuture<Void>()
        for (buffer in buffers) {
            val chunk = instance.getChunk(buffer.chunkX, buffer.chunkZ) ?: continue
            buffer.applyTo(chunk)
        }
        future.complete(null)
        return future
    }

    fun chunkCount(): Int = chunks.size

    fun shouldFlush(): Boolean = chunks.size >= BACKPRESSURE_THRESHOLD

    private fun packKey(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
}
