package me.nebula.orbit.utils.nebulaworld

import com.github.luben.zstd.Zstd
import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path

object NebulaWorldWriter {

    fun write(world: NebulaWorld, compression: CompressionType = CompressionType.ZSTD): ByteArray {
        val payload = writePayload(world)

        val compressed = when (compression) {
            CompressionType.NONE -> payload
            CompressionType.ZSTD -> Zstd.compress(payload)
        }

        val headerSize = 4 + 2 + varIntSize(world.dataVersion) + 1 + varIntSize(payload.size) + compressed.size
        val buf = ByteBuffer.allocate(headerSize)
        buf.putInt(NEBULA_MAGIC)
        buf.putShort(NEBULA_VERSION)
        writeVarInt(buf, world.dataVersion)
        buf.put(compression.id)
        writeVarInt(buf, payload.size)
        buf.put(compressed)

        return buf.array().copyOf(buf.position())
    }

    fun write(world: NebulaWorld, path: Path, compression: CompressionType = CompressionType.ZSTD) {
        Files.write(path, write(world, compression))
    }

    private fun writePayload(world: NebulaWorld): ByteArray {
        val buf = GrowableBuffer()

        buf.putByte(world.minSection.toByte())
        buf.putByte(world.maxSection.toByte())
        buf.putByteArray(world.userData)
        buf.putVarInt(world.chunks.size)

        for (chunk in world.chunks.values) {
            writeChunk(buf, chunk)
        }

        return buf.toByteArray()
    }

    private fun writeChunk(buf: GrowableBuffer, chunk: NebulaChunk) {
        buf.putVarInt(chunk.x)
        buf.putVarInt(chunk.z)

        for (section in chunk.sections) {
            writeSection(buf, section)
        }

        buf.putVarInt(chunk.blockEntities.size)
        for (be in chunk.blockEntities) {
            writeBlockEntity(buf, be)
        }

        buf.putByteArray(chunk.userData)
    }

    private fun writeSection(buf: GrowableBuffer, section: NebulaSection) {
        buf.putByte(if (section.isEmpty) 1 else 0)
        if (section.isEmpty) return

        buf.putVarInt(section.blockPalette.size)
        for (entry in section.blockPalette) buf.putString(entry)
        if (section.blockPalette.size > 1) {
            val bpe = PaletteUtil.bitsPerEntry(section.blockPalette.size)
            val longs = PaletteUtil.pack(section.blockData, bpe)
            buf.putLongArray(longs)
        }

        buf.putVarInt(section.biomePalette.size)
        for (entry in section.biomePalette) buf.putString(entry)
        if (section.biomePalette.size > 1) {
            val bpe = PaletteUtil.bitsPerEntry(section.biomePalette.size)
            val longs = PaletteUtil.pack(section.biomeData, bpe)
            buf.putLongArray(longs)
        }

        writeLightData(buf, section.blockLight)
        writeLightData(buf, section.skyLight)
    }

    private fun writeLightData(buf: GrowableBuffer, light: LightData) {
        buf.putByte(light.content.id)
        if (light.content == LightContent.PRESENT && light.data != null) {
            buf.putBytes(light.data)
        }
    }

    private fun writeBlockEntity(buf: GrowableBuffer, be: NebulaBlockEntity) {
        val packed = (be.x and 0xF) or ((be.z and 0xF) shl 4) or (be.y shl 8)
        buf.putVarInt(packed)

        if (be.id != null) {
            buf.putByte(1)
            buf.putString(be.id)
        } else {
            buf.putByte(0)
        }

        if (be.nbt != null) {
            buf.putByte(1)
            val nbtBytes = ByteArrayOutputStream().also { BinaryTagIO.writer().write(be.nbt, it) }.toByteArray()
            buf.putByteArray(nbtBytes)
        } else {
            buf.putByte(0)
        }
    }

    private fun writeVarInt(buf: ByteBuffer, value: Int) {
        var v = value
        while (true) {
            if (v and 0x7F.inv() == 0) {
                buf.put(v.toByte())
                return
            }
            buf.put(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
    }

    private fun varIntSize(value: Int): Int {
        var v = value
        var size = 0
        do {
            size++
            v = v ushr 7
        } while (v != 0)
        return size
    }
}

class GrowableBuffer {

    private var data = ByteArray(8192)
    private var pos = 0

    private fun ensureCapacity(needed: Int) {
        if (pos + needed > data.size) {
            data = data.copyOf(maxOf(data.size * 2, pos + needed))
        }
    }

    fun putByte(b: Byte) {
        ensureCapacity(1)
        data[pos++] = b
    }

    fun putBytes(bytes: ByteArray) {
        ensureCapacity(bytes.size)
        System.arraycopy(bytes, 0, data, pos, bytes.size)
        pos += bytes.size
    }

    fun putInt(v: Int) {
        ensureCapacity(4)
        data[pos++] = (v shr 24).toByte()
        data[pos++] = (v shr 16).toByte()
        data[pos++] = (v shr 8).toByte()
        data[pos++] = v.toByte()
    }

    fun putLong(v: Long) {
        ensureCapacity(8)
        data[pos++] = (v shr 56).toByte()
        data[pos++] = (v shr 48).toByte()
        data[pos++] = (v shr 40).toByte()
        data[pos++] = (v shr 32).toByte()
        data[pos++] = (v shr 24).toByte()
        data[pos++] = (v shr 16).toByte()
        data[pos++] = (v shr 8).toByte()
        data[pos++] = v.toByte()
    }

    fun putVarInt(value: Int) {
        var v = value
        while (true) {
            if (v and 0x7F.inv() == 0) {
                putByte(v.toByte())
                return
            }
            putByte(((v and 0x7F) or 0x80).toByte())
            v = v ushr 7
        }
    }

    fun putString(s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        putVarInt(bytes.size)
        putBytes(bytes)
    }

    fun putByteArray(bytes: ByteArray) {
        putVarInt(bytes.size)
        putBytes(bytes)
    }

    fun putLongArray(longs: LongArray) {
        putVarInt(longs.size)
        for (l in longs) putLong(l)
    }

    fun position(): Int = pos

    fun toByteArray(): ByteArray = data.copyOf(pos)
}
