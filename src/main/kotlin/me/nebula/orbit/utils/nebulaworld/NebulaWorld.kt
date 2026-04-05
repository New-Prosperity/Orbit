package me.nebula.orbit.utils.nebulaworld

import net.kyori.adventure.nbt.CompoundBinaryTag

const val NEBULA_MAGIC = 0x4E65624C
const val NEBULA_VERSION: Short = 1
const val SECTION_BLOCK_COUNT = 4096
const val SECTION_BIOME_COUNT = 64
const val LIGHT_ARRAY_SIZE = 2048

enum class CompressionType(val id: Byte) {
    NONE(0),
    ZSTD(1);

    companion object {
        fun fromId(id: Byte): CompressionType = entries.first { it.id == id }
    }
}

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
    val blockPalette: Array<String> = emptyArray(),
    val blockData: IntArray = IntArray(0),
    val biomePalette: Array<String> = emptyArray(),
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

class NebulaWorld(
    val dataVersion: Int,
    val minSection: Int,
    val maxSection: Int,
    val userData: ByteArray = ByteArray(0),
    val chunks: Map<Long, NebulaChunk>,
) {
    val sectionCount: Int get() = maxSection - minSection + 1

    fun chunkAt(x: Int, z: Int): NebulaChunk? = chunks[packChunkKey(x, z)]

    companion object {
        fun packChunkKey(x: Int, z: Int): Long =
            (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}
