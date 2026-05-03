package me.nebula.orbit.utils.nebulaworld

import net.kyori.adventure.nbt.BinaryTagIO
import net.kyori.adventure.nbt.CompoundBinaryTag
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

object ChunkPayloadCodec {

    fun encode(chunk: NebulaChunk, includeLight: Boolean): ByteArray {
        val buf = GrowableBuffer()
        var bitmask = 0L
        for (i in chunk.sections.indices) {
            if (!chunk.sections[i].isEmpty) bitmask = bitmask or (1L shl i)
        }
        buf.putLong(bitmask)

        for (i in chunk.sections.indices) {
            val section = chunk.sections[i]
            if (section.isEmpty) continue
            writeSection(buf, section, includeLight)
        }

        buf.putVarInt(chunk.blockEntities.size)
        for (be in chunk.blockEntities) writeBlockEntity(buf, be)

        buf.putByteArray(chunk.userData)
        return buf.toByteArray()
    }

    fun decode(x: Int, z: Int, data: ByteArray, sectionCount: Int, includeLight: Boolean): NebulaChunk {
        val buf = ByteCursor(data)
        val bitmask = buf.getLong()

        val sections = Array(sectionCount) { NebulaSection.EMPTY }
        for (i in 0 until sectionCount) {
            if (bitmask and (1L shl i) == 0L) continue
            sections[i] = readSection(buf, includeLight)
        }

        val beCount = buf.getVarInt()
        val blockEntities = ArrayList<NebulaBlockEntity>(beCount)
        repeat(beCount) { blockEntities += readBlockEntity(buf) }

        val userData = buf.getByteArray()

        return NebulaChunk(x, z, sections, blockEntities, userData)
    }

    private fun writeSection(buf: GrowableBuffer, section: NebulaSection, includeLight: Boolean) {
        buf.putVarInt(section.blockPaletteRefs.size)
        for (ref in section.blockPaletteRefs) buf.putVarInt(ref)
        if (section.blockPaletteRefs.size > 1) {
            val bpe = PaletteUtil.bitsPerEntry(section.blockPaletteRefs.size)
            val longs = PaletteUtil.pack(section.blockData, bpe)
            buf.putByte(bpe.toByte())
            buf.putVarInt(longs.size)
            for (l in longs) buf.putLong(l)
        }

        buf.putVarInt(section.biomePaletteRefs.size)
        for (ref in section.biomePaletteRefs) buf.putVarInt(ref)
        if (section.biomePaletteRefs.size > 1) {
            val bpe = PaletteUtil.bitsPerEntry(section.biomePaletteRefs.size)
            val longs = PaletteUtil.pack(section.biomeData, bpe)
            buf.putByte(bpe.toByte())
            buf.putVarInt(longs.size)
            for (l in longs) buf.putLong(l)
        }

        if (includeLight) {
            writeLight(buf, section.blockLight)
            writeLight(buf, section.skyLight)
        }
    }

    private fun readSection(buf: ByteCursor, includeLight: Boolean): NebulaSection {
        val blockPaletteSize = buf.getVarInt()
        val blockPaletteRefs = IntArray(blockPaletteSize) { buf.getVarInt() }
        val blockData = if (blockPaletteSize > 1) {
            val bpe = buf.getByte().toInt() and 0xFF
            val longCount = buf.getVarInt()
            val longs = LongArray(longCount) { buf.getLong() }
            PaletteUtil.unpack(longs, bpe, SECTION_BLOCK_COUNT)
        } else {
            IntArray(SECTION_BLOCK_COUNT)
        }

        val biomePaletteSize = buf.getVarInt()
        val biomePaletteRefs = IntArray(biomePaletteSize) { buf.getVarInt() }
        val biomeData = if (biomePaletteSize > 1) {
            val bpe = buf.getByte().toInt() and 0xFF
            val longCount = buf.getVarInt()
            val longs = LongArray(longCount) { buf.getLong() }
            PaletteUtil.unpack(longs, bpe, SECTION_BIOME_COUNT)
        } else {
            IntArray(SECTION_BIOME_COUNT)
        }

        val blockLight = if (includeLight) readLight(buf) else LightData.MISSING
        val skyLight = if (includeLight) readLight(buf) else LightData.MISSING

        return NebulaSection(false, blockPaletteRefs, blockData, biomePaletteRefs, biomeData, blockLight, skyLight)
    }

    private fun writeLight(buf: GrowableBuffer, light: LightData) {
        buf.putByte(light.content.id)
        if (light.content == LightContent.PRESENT && light.data != null) {
            buf.putBytes(light.data)
        }
    }

    private fun readLight(buf: ByteCursor): LightData {
        val content = LightContent.fromId(buf.getByte())
        return if (content == LightContent.PRESENT) {
            val data = ByteArray(LIGHT_ARRAY_SIZE)
            buf.getBytes(data)
            LightData(content, data)
        } else {
            LightData(content, null)
        }
    }

    private fun writeBlockEntity(buf: GrowableBuffer, be: NebulaBlockEntity) {
        buf.putByte((((be.x and 0xF) or ((be.z and 0xF) shl 4))).toByte())
        buf.putVarInt(PaletteUtil.zigzagEncode(be.y))

        val idBytes = be.id?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        buf.putVarInt(idBytes.size)
        buf.putBytes(idBytes)

        if (be.nbt != null) {
            val nbtBytes = ByteArrayOutputStream().also { BinaryTagIO.writer().write(be.nbt, it) }.toByteArray()
            buf.putVarInt(nbtBytes.size)
            buf.putBytes(nbtBytes)
        } else {
            buf.putVarInt(0)
        }
    }

    private fun readBlockEntity(buf: ByteCursor): NebulaBlockEntity {
        val xz = buf.getByte().toInt() and 0xFF
        val x = xz and 0xF
        val z = (xz shr 4) and 0xF
        val y = PaletteUtil.zigzagDecode(buf.getVarInt())

        val idLen = buf.getVarInt()
        val id = if (idLen > 0) String(buf.getBytesArr(idLen), Charsets.UTF_8) else null

        val nbtLen = buf.getVarInt()
        val nbt: CompoundBinaryTag? = if (nbtLen > 0) {
            BinaryTagIO.reader().read(ByteArrayInputStream(buf.getBytesArr(nbtLen)))
        } else null

        return NebulaBlockEntity(x, y, z, id, nbt)
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

    fun putShort(v: Short) {
        ensureCapacity(2)
        data[pos++] = (v.toInt() shr 8).toByte()
        data[pos++] = v.toByte()
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

    fun position(): Int = pos

    fun toByteArray(): ByteArray = data.copyOf(pos)
}

class ByteCursor(private val data: ByteArray, private var pos: Int = 0) {

    fun position(): Int = pos
    fun seek(p: Int) { pos = p }
    fun remaining(): Int = data.size - pos

    fun getByte(): Byte = data[pos++]

    fun getBytes(out: ByteArray) {
        System.arraycopy(data, pos, out, 0, out.size)
        pos += out.size
    }

    fun getBytesArr(len: Int): ByteArray {
        val out = ByteArray(len)
        System.arraycopy(data, pos, out, 0, len)
        pos += len
        return out
    }

    fun getShort(): Short {
        val hi = data[pos++].toInt() and 0xFF
        val lo = data[pos++].toInt() and 0xFF
        return ((hi shl 8) or lo).toShort()
    }

    fun getInt(): Int {
        val a = data[pos++].toInt() and 0xFF
        val b = data[pos++].toInt() and 0xFF
        val c = data[pos++].toInt() and 0xFF
        val d = data[pos++].toInt() and 0xFF
        return (a shl 24) or (b shl 16) or (c shl 8) or d
    }

    fun getLong(): Long {
        val hi = getInt().toLong() and 0xFFFFFFFFL
        val lo = getInt().toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    fun getVarInt(): Int {
        var value = 0
        var shift = 0
        var b: Int
        do {
            b = data[pos++].toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            shift += 7
        } while (b and 0x80 != 0)
        return value
    }

    fun getString(): String {
        val length = getVarInt()
        return String(getBytesArr(length), Charsets.UTF_8)
    }

    fun getByteArray(): ByteArray {
        val length = getVarInt()
        return getBytesArr(length)
    }
}
