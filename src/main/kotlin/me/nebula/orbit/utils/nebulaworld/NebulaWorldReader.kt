package me.nebula.orbit.utils.nebulaworld

import com.github.luben.zstd.Zstd
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

object NebulaWorldReader {

    fun read(path: Path): NebulaWorld = read(Files.readAllBytes(path))

    fun read(data: ByteArray): NebulaWorld {
        val buf = ByteBuffer.wrap(data)

        val magic = buf.int
        require(magic == NEBULA_MAGIC) { "Invalid magic: 0x${magic.toString(16)}, expected 0x${NEBULA_MAGIC.toString(16)}" }

        val version = buf.short
        require(version <= NEBULA_VERSION) { "Unsupported version: $version, max supported: $NEBULA_VERSION" }

        val dataVersion = readVarInt(buf)
        val compression = CompressionType.fromId(buf.get())
        val uncompressedLength = readVarInt(buf)

        val remaining = ByteArray(buf.remaining())
        buf.get(remaining)

        val payload = when (compression) {
            CompressionType.NONE -> remaining
            CompressionType.ZSTD -> {
                val output = ByteArray(uncompressedLength)
                Zstd.decompress(output, remaining)
                output
            }
        }

        return readPayload(ByteBuffer.wrap(payload))
    }

    private fun readPayload(buf: ByteBuffer): NebulaWorld {
        val minSection = buf.get().toInt()
        val maxSection = buf.get().toInt()
        val sectionCount = maxSection - minSection + 1

        val userData = readByteArray(buf)
        val chunkCount = readVarInt(buf)

        val chunks = HashMap<Long, NebulaChunk>(chunkCount)
        repeat(chunkCount) {
            val chunk = readChunk(buf, sectionCount)
            chunks[NebulaWorld.packChunkKey(chunk.x, chunk.z)] = chunk
        }

        return NebulaWorld(0, minSection, maxSection, userData, chunks)
    }

    private fun readChunk(buf: ByteBuffer, sectionCount: Int): NebulaChunk {
        val x = readVarInt(buf)
        val z = readVarInt(buf)

        val sections = Array(sectionCount) { readSection(buf) }

        val blockEntityCount = readVarInt(buf)
        val blockEntities = (0 until blockEntityCount).map { readBlockEntity(buf) }

        val userData = readByteArray(buf)

        return NebulaChunk(x, z, sections, blockEntities, userData)
    }

    private fun readSection(buf: ByteBuffer): NebulaSection {
        val isEmpty = buf.get() != 0.toByte()
        if (isEmpty) return NebulaSection.EMPTY

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

        return NebulaSection(false, blockPalette, blockData, biomePalette, biomeData, blockLight, skyLight)
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
        val nbt = if (hasNbt) readNbt(buf) else null

        return NebulaBlockEntity(x, y, z, id, nbt)
    }

    private fun readNbt(buf: ByteBuffer): CompoundBinaryTag {
        val bytes = readByteArray(buf)
        return BinaryTagIO.reader().read(ByteArrayInputStream(bytes))
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
