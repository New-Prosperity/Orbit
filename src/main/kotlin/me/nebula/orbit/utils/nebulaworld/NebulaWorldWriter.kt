package me.nebula.orbit.utils.nebulaworld

import com.github.luben.zstd.Zstd
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import kotlin.io.path.writeBytes

class WritableSection(
    val isEmpty: Boolean,
    val blockPaletteNames: Array<String> = emptyArray(),
    val blockData: IntArray = IntArray(0),
    val biomePaletteNames: Array<String> = emptyArray(),
    val biomeData: IntArray = IntArray(0),
    val blockLight: LightData = LightData.MISSING,
    val skyLight: LightData = LightData.MISSING,
) {
    companion object {
        val EMPTY = WritableSection(isEmpty = true)
    }
}

class WritableChunk(
    val x: Int,
    val z: Int,
    val sections: Array<WritableSection>,
    val blockEntities: List<NebulaBlockEntity>,
    val userData: ByteArray = ByteArray(0),
)

class WritableWorld(
    val dataVersion: Int,
    val minSection: Int,
    val maxSection: Int,
    val includeLight: Boolean,
    val userData: ByteArray,
    val chunks: Collection<WritableChunk>,
)

object NebulaWorldWriter {

    fun write(world: WritableWorld, zstdLevel: Int = 6): ByteArray {
        val flags = if (world.includeLight) FLAG_INCLUDE_LIGHT else 0

        val blockNameToGlobal = HashMap<String, Int>()
        val biomeNameToGlobal = HashMap<String, Int>()
        val globalBlockList = ArrayList<String>()
        val globalBiomeList = ArrayList<String>()

        fun internBlock(name: String): Int = blockNameToGlobal.getOrPut(name) {
            globalBlockList += name
            globalBlockList.size - 1
        }
        fun internBiome(name: String): Int = biomeNameToGlobal.getOrPut(name) {
            globalBiomeList += name
            globalBiomeList.size - 1
        }

        val chunkData = GrowableBuffer()
        val slots = ArrayList<SlotEntry>(world.chunks.size)

        for (chunk in world.chunks) {
            val sections = Array(chunk.sections.size) { idx ->
                val src = chunk.sections[idx]
                if (src.isEmpty) {
                    NebulaSection.EMPTY
                } else {
                    val blockRefs = IntArray(src.blockPaletteNames.size) { internBlock(src.blockPaletteNames[it]) }
                    val biomeRefs = IntArray(src.biomePaletteNames.size) { internBiome(src.biomePaletteNames[it]) }
                    NebulaSection(
                        isEmpty = false,
                        blockPaletteRefs = blockRefs,
                        blockData = src.blockData,
                        biomePaletteRefs = biomeRefs,
                        biomeData = src.biomeData,
                        blockLight = src.blockLight,
                        skyLight = src.skyLight,
                    )
                }
            }
            val nebulaChunk = NebulaChunk(chunk.x, chunk.z, sections, chunk.blockEntities, chunk.userData)
            val payload = ChunkPayloadCodec.encode(nebulaChunk, world.includeLight)
            val compressed = Zstd.compress(payload, zstdLevel)
            slots += SlotEntry(chunk.x, chunk.z, chunkData.position(), compressed.size, payload.size)
            chunkData.putBytes(compressed)
        }

        val out = GrowableBuffer()
        out.putInt(NEBULA_MAGIC)
        out.putShort(NEBULA_VERSION)
        out.putInt(flags)
        out.putVarInt(world.dataVersion)
        out.putByte(world.minSection.toByte())
        out.putByte(world.maxSection.toByte())
        out.putByteArray(world.userData)

        out.putVarInt(globalBlockList.size)
        for (entry in globalBlockList) out.putString(entry)
        out.putVarInt(globalBiomeList.size)
        for (entry in globalBiomeList) out.putString(entry)

        out.putVarInt(slots.size)
        for (slot in slots) {
            out.putInt(slot.chunkX)
            out.putInt(slot.chunkZ)
            out.putInt(slot.offset)
            out.putInt(slot.compressedLen)
            out.putInt(slot.uncompressedLen)
        }

        out.putInt(chunkData.position())
        out.putBytes(chunkData.toByteArray())

        return out.toByteArray()
    }

    fun write(world: WritableWorld, path: Path, zstdLevel: Int = 6) {
        path.writeBytes(write(world, zstdLevel))
    }

    private class SlotEntry(
        val chunkX: Int,
        val chunkZ: Int,
        val offset: Int,
        val compressedLen: Int,
        val uncompressedLen: Int,
    )
}

internal fun nbtToBytes(nbt: CompoundBinaryTag): ByteArray =
    ByteArrayOutputStream().also { BinaryTagIO.writer().write(nbt, it) }.toByteArray()
