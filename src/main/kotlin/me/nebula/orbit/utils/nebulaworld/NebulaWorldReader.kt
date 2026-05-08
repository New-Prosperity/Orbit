package me.nebula.orbit.utils.nebulaworld

import me.nebula.ether.utils.logging.logger
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes

object NebulaWorldReader {

    private val logger = logger("NebulaWorldReader")

    fun read(path: Path): NebulaWorld {
        val bytes = path.readBytes()
        val version = peekVersion(bytes)
        if (version == LEGACY_V1_VERSION) {
            val v2Bytes = upgradeV1ToV2Bytes(bytes)
            runCatching { path.writeBytes(v2Bytes) }
                .onFailure { logger.warn { "v1→v2 in-memory upgrade succeeded but disk write to $path failed: ${it.message}" } }
                .onSuccess { logger.info { "Upgraded ${path.fileName} from v1 to v2 (${bytes.size / 1024}KB → ${v2Bytes.size / 1024}KB) on disk" } }
            return read(v2Bytes)
        }
        return read(bytes)
    }

    fun read(data: ByteArray): NebulaWorld {
        val version = peekVersion(data)
        if (version == LEGACY_V1_VERSION) {
            logger.info { "Reading legacy v1 .nebula payload — upgrading to v2 in memory (no disk write)" }
            return read(upgradeV1ToV2Bytes(data))
        }

        val cur = ByteCursor(data)

        val magic = cur.getInt()
        require(magic == NEBULA_MAGIC) {
            "Invalid magic: 0x${magic.toString(16)}, expected 0x${NEBULA_MAGIC.toString(16)}"
        }

        val parsedVersion = cur.getShort()
        require(parsedVersion == NEBULA_VERSION) {
            "Unsupported version: $parsedVersion, this build expects $NEBULA_VERSION"
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

    private fun peekVersion(data: ByteArray): Short {
        require(data.size >= 6) { "Buffer too short to contain magic+version" }
        val magic = ((data[0].toInt() and 0xFF) shl 24) or
            ((data[1].toInt() and 0xFF) shl 16) or
            ((data[2].toInt() and 0xFF) shl 8) or
            (data[3].toInt() and 0xFF)
        require(magic == NEBULA_MAGIC) {
            "Invalid magic: 0x${magic.toString(16)}, expected 0x${NEBULA_MAGIC.toString(16)}"
        }
        val hi = data[4].toInt() and 0xFF
        val lo = data[5].toInt() and 0xFF
        return ((hi shl 8) or lo).toShort()
    }

    private fun upgradeV1ToV2Bytes(data: ByteArray): ByteArray {
        val writable = NebulaWorldV1Reader.readAsWritable(data)
        return NebulaWorldWriter.write(writable)
    }
}
