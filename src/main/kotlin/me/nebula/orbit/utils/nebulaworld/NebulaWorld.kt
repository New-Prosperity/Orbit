package me.nebula.orbit.utils.nebulaworld

import com.github.luben.zstd.Zstd
import me.nebula.ether.utils.logging.logger
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.util.concurrent.ConcurrentHashMap

private val nebulaWorldLogger = logger("NebulaWorld")

const val NEBULA_MAGIC = 0x4E65624C
const val NEBULA_VERSION: Short = 2
const val SECTION_BLOCK_COUNT = 4096
const val SECTION_BIOME_COUNT = 64
const val LIGHT_ARRAY_SIZE = 2048

const val FLAG_INCLUDE_LIGHT = 1 shl 0

enum class LightContent(val id: Byte) {
    MISSING(0),
    EMPTY(1),
    FULL(2),
    PRESENT(3);

    companion object {
        fun fromId(id: Byte): LightContent = entries.first { it.id == id }
    }
}

class LightData(val content: LightContent, val data: ByteArray?) {
    companion object {
        val MISSING = LightData(LightContent.MISSING, null)
        val EMPTY = LightData(LightContent.EMPTY, null)
        val FULL = LightData(LightContent.FULL, null)
    }
}

class NebulaBlockEntity(
    val x: Int,
    val y: Int,
    val z: Int,
    val id: String?,
    val nbt: CompoundBinaryTag?,
)

class NebulaSection(
    val isEmpty: Boolean,
    val blockPaletteRefs: IntArray = IntArray(0),
    val blockData: IntArray = IntArray(0),
    val biomePaletteRefs: IntArray = IntArray(0),
    val biomeData: IntArray = IntArray(0),
    val blockLight: LightData = LightData.MISSING,
    val skyLight: LightData = LightData.MISSING,
) {
    companion object {
        val EMPTY = NebulaSection(isEmpty = true)
    }
}

class NebulaChunk(
    val x: Int,
    val z: Int,
    val sections: Array<NebulaSection>,
    val blockEntities: List<NebulaBlockEntity>,
    val userData: ByteArray = ByteArray(0),
)

class ChunkSlot(
    val offset: Int,
    val compressedLen: Int,
    val uncompressedLen: Int,
)

class NebulaWorld(
    val dataVersion: Int,
    val minSection: Int,
    val maxSection: Int,
    val flags: Int,
    val userData: ByteArray,
    val globalBlockPalette: Array<String>,
    val globalBiomePalette: Array<String>,
    private val rawChunkData: ByteArray,
    private val slots: Map<Long, ChunkSlot>,
) {
    val sectionCount: Int get() = maxSection - minSection + 1
    val includeLight: Boolean get() = flags and FLAG_INCLUDE_LIGHT != 0
    val chunkCount: Int get() = slots.size

    private val parsed = ConcurrentHashMap<Long, NebulaChunk>()
    private val brokenSlots = ConcurrentHashMap.newKeySet<Long>()

    fun chunkAt(x: Int, z: Int): NebulaChunk? {
        val key = packChunkKey(x, z)
        parsed[key]?.let { return it }
        if (key in brokenSlots) return null
        val slot = slots[key] ?: return null
        return try {
            parsed.computeIfAbsent(key) { decodeSlot(x, z, slot) }
        } catch (t: Throwable) {
            if (brokenSlots.add(key)) {
                nebulaWorldLogger.warn { "Chunk decode failed at ($x, $z) — chunk will be skipped: ${t.message}" }
            }
            null
        }
    }

    fun chunkKeys(): Set<Long> = slots.keys

    fun forEachChunk(action: (NebulaChunk) -> Unit) {
        for (key in slots.keys) {
            val x = (key shr 32).toInt()
            val z = key.toInt()
            chunkAt(x, z)?.let(action)
        }
    }

    private fun decodeSlot(x: Int, z: Int, slot: ChunkSlot): NebulaChunk {
        val decompressed = ByteArray(slot.uncompressedLen)
        Zstd.decompressByteArray(decompressed, 0, slot.uncompressedLen, rawChunkData, slot.offset, slot.compressedLen)
        return ChunkPayloadCodec.decode(x, z, decompressed, sectionCount, includeLight)
    }

    companion object {
        fun packChunkKey(x: Int, z: Int): Long =
            (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}
