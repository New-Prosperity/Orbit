package me.nebula.orbit.utils.nebulaworld

import java.nio.file.Path
import kotlin.io.path.readBytes

object NebulaWorldReader {

    fun read(path: Path): NebulaWorld = read(path.readBytes())

    fun read(data: ByteArray): NebulaWorld {
        val cur = ByteCursor(data)

        val magic = cur.getInt()
        require(magic == NEBULA_MAGIC) {
            "Invalid magic: 0x${magic.toString(16)}, expected 0x${NEBULA_MAGIC.toString(16)}"
        }

        val version = cur.getShort()
        require(version == NEBULA_VERSION) {
            "Unsupported version: $version, this build expects $NEBULA_VERSION"
        }

        val flags = cur.getInt()
        val dataVersion = cur.getVarInt()
        val minSection = cur.getByte().toInt()
        val maxSection = cur.getByte().toInt()
        val userData = cur.getByteArray()

        val blockPaletteSize = cur.getVarInt()
        val globalBlockPalette = Array(blockPaletteSize) { cur.getString() }
        val biomePaletteSize = cur.getVarInt()
        val globalBiomePalette = Array(biomePaletteSize) { cur.getString() }

        val slotCount = cur.getVarInt()
        val slots = HashMap<Long, ChunkSlot>(slotCount)
        val coords = LongArray(slotCount)
        for (i in 0 until slotCount) {
            val cx = cur.getInt()
            val cz = cur.getInt()
            val offset = cur.getInt()
            val compressedLen = cur.getInt()
            val uncompressedLen = cur.getInt()
            val key = NebulaWorld.packChunkKey(cx, cz)
            slots[key] = ChunkSlot(offset, compressedLen, uncompressedLen)
            coords[i] = key
        }

        val chunkDataLen = cur.getInt()
        val rawChunkData = cur.getBytesArr(chunkDataLen)

        return NebulaWorld(
            dataVersion = dataVersion,
            minSection = minSection,
            maxSection = maxSection,
            flags = flags,
            userData = userData,
            globalBlockPalette = globalBlockPalette,
            globalBiomePalette = globalBiomePalette,
            rawChunkData = rawChunkData,
            slots = slots,
        )
    }
}
