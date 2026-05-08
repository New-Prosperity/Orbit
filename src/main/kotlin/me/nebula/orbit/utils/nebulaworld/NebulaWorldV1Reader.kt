package me.nebula.orbit.utils.nebulaworld

import com.github.luben.zstd.Zstd
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer

object NebulaWorldV1Reader {

        private const val V1_VERSION: Short = 1

    fun readAsWritable(data: ByteArray): WritableWorld {
        val buf = ByteBuffer.wrap(data)

        val magic = buf.int
        require(magic == NEBULA_MAGIC) {
            "Invalid magic: 0x${magic.toString(16)}, expected 0x${NEBULA_MAGIC.toString(16)}"
        }
        val version = buf.short
        require(version == V1_VERSION) {
            "NebulaWorldV1Reader only handles v1 (got version $version)"
        }

        val dataVersion = readVarInt(buf)
        val compressionId = buf.get()
        val uncompressedLength = readVarInt(buf)

        val remaining = ByteArray(buf.remaining())
        buf.get(remaining)

        val payload = when (compressionId.toInt()) {
            0 -> remaining
            1 -> {
                val output = ByteArray(uncompressedLength)
                Zstd.decompress(output, remaining)
                output
            }
            else -> error("Unknown v1 compression id: $compressionId")
        }

        return readPayload(ByteBuffer.wrap(payload), dataVersion)
    }

    private fun readPayload(buf: ByteBuffer, dataVersion: Int): WritableWorld {
        val minSection = buf.get().toInt()
        val maxSection = buf.get().toInt()
        val sectionCount = maxSection - minSection + 1

        val userData = readByteArray(buf)
        val chunkCount = readVarInt(buf)

        val chunks = ArrayList<WritableChunk>(chunkCount)
        var hasAnyLight = false
        repeat(chunkCount) {
            val (chunk, chunkHasLight) = readChunk(buf, sectionCount)
            chunks += chunk
            if (chunkHasLight) hasAnyLight = true
        }

        return WritableWorld(
            dataVersion = dataVersion,
            minSection = minSection,
            maxSection = maxSection,
            includeLight = hasAnyLight,
            userData = userData,
            chunks = chunks,
        )
    }

    private fun readChunk(buf: ByteBuffer, sectionCount: Int): Pair<WritableChunk, Boolean> {
        val x = readVarInt(buf)
        val z = readVarInt(buf)

        var hasLight = false
        val sections = Array(sectionCount) {
            val (section, sectionHasLight) = readSection(buf)
            if (sectionHasLight) hasLight = true
            section
        }

        val blockEntityCount = readVarInt(buf)
        val blockEntities = ArrayList<NebulaBlockEntity>(blockEntityCount)
        repeat(blockEntityCount) { blockEntities += readBlockEntity(buf) }

        val userData = readByteArray(buf)
        return WritableChunk(x, z, sections, blockEntities, userData) to hasLight
    }

    private fun readSection(buf: ByteBuffer): Pair<WritableSection, Boolean> {
        val isEmpty = buf.get() != 0.toByte()
        if (isEmpty) return WritableSection.EMPTY to false

        val blockPaletteSize = readVarInt(buf)
        val blockPalette = Array(blockPaletteSize) { readString(buf) }
        val blockData = if (blockPaletteSize > 1) {
            val bpe = PaletteUtil.bitsPerEntry(blockPaletteSize)
            val longs = readLongArray(buf)
            PaletteUtil.unpack(longs, bpe, SECTION_BLOCK_COUNT)
        } else {
            IntArray(SECTION_BLOCK_COUNT)
        }

        val biomePaletteSize = readVarInt(buf)
        val biomePalette = Array(biomePaletteSize) { readString(buf) }
        val biomeData = if (biomePaletteSize > 1) {
            val bpe = PaletteUtil.bitsPerEntry(biomePaletteSize)
            val longs = readLongArray(buf)
            PaletteUtil.unpack(longs, bpe, SECTION_BIOME_COUNT)
        } else {
            IntArray(SECTION_BIOME_COUNT)
        }

        val blockLight = readLightData(buf)
        val skyLight = readLightData(buf)

        val hasLight = blockLight.content == LightContent.PRESENT || skyLight.content == LightContent.PRESENT
        return WritableSection(
            isEmpty = false,
            blockPaletteNames = blockPalette,
            blockData = blockData,
            biomePaletteNames = biomePalette,
            biomeData = biomeData,
            blockLight = blockLight,
            skyLight = skyLight,
        ) to hasLight
    }

    private fun readLightData(buf: ByteBuffer): LightData {
        val content = LightContent.fromId(buf.get())
        return if (content == LightContent.PRESENT) {
            val data = ByteArray(LIGHT_ARRAY_SIZE)
            buf.get(data)
            LightData(content, data)
        } else {
            LightData(content, null)
        }
    }

    private fun readBlockEntity(buf: ByteBuffer): NebulaBlockEntity {
        val packed = readVarInt(buf)
        val x = packed and 0xF
        val z = (packed shr 4) and 0xF
        val y = packed shr 8

        val hasId = buf.get() != 0.toByte()
        val id = if (hasId) readString(buf) else null

        val hasNbt = buf.get() != 0.toByte()
        val nbt: CompoundBinaryTag? = if (hasNbt) {
            val nbtBytes = readByteArray(buf)
            BinaryTagIO.reader().read(ByteArrayInputStream(nbtBytes))
        } else null

        return NebulaBlockEntity(x, y, z, id, nbt)
    }

    private fun readVarInt(buf: ByteBuffer): Int {
        var value = 0
        var shift = 0
        var b: Int
        do {
            b = buf.get().toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            shift += 7
        } while (b and 0x80 != 0)
        return value
    }

    private fun readString(buf: ByteBuffer): String {
        val length = readVarInt(buf)
        val bytes = ByteArray(length)
        buf.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readByteArray(buf: ByteBuffer): ByteArray {
        val length = readVarInt(buf)
        val bytes = ByteArray(length)
        buf.get(bytes)
        return bytes
    }

    private fun readLongArray(buf: ByteBuffer): LongArray {
        val count = readVarInt(buf)
        return LongArray(count) { buf.long }
    }
}
