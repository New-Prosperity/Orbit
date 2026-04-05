package me.nebula.orbit.utils.worldedit

import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import java.io.ByteArrayOutputStream

class ChangeSet(private val maxMemoryMB: Int = 10) {

    private val stream = ByteArrayOutputStream(4096)
    private var lastX = 0
    private var lastY = 0
    private var lastZ = 0
    private var count = 0
    private var closed = false

    val changeCount: Int get() = count

    fun add(x: Int, y: Int, z: Int, fromOrdinal: Short, toOrdinal: Short) {
        if (closed) return
        if (sizeBytes() > maxMemoryMB * 1024L * 1024L) return

        writeVarInt(x - lastX)
        writeVarInt(y - lastY)
        writeVarInt(z - lastZ)
        writeVarInt(fromOrdinal.toInt())
        writeVarInt(toOrdinal.toInt())

        lastX = x
        lastY = y
        lastZ = z
        count++
    }

    fun undo(instance: Instance) {
        val entries = decode()
        for (i in entries.indices.reversed()) {
            val e = entries[i]
            instance.setBlock(e.x, e.y, e.z, Block.fromStateId(e.fromOrdinal.toInt())
                ?: Block.AIR)
        }
    }

    fun redo(instance: Instance) {
        val entries = decode()
        for (e in entries) {
            instance.setBlock(e.x, e.y, e.z, Block.fromStateId(e.toOrdinal.toInt())
                ?: Block.AIR)
        }
    }

    fun sizeBytes(): Long = stream.size().toLong()

    fun close() {
        closed = true
    }

    private fun decode(): List<ChangeEntry> {
        val data = stream.toByteArray()
        val entries = mutableListOf<ChangeEntry>()
        var pos = 0
        var cx = 0
        var cy = 0
        var cz = 0

        while (pos < data.size) {
            val (dx, p1) = readVarInt(data, pos)
            val (dy, p2) = readVarInt(data, p1)
            val (dz, p3) = readVarInt(data, p2)
            val (from, p4) = readVarInt(data, p3)
            val (to, p5) = readVarInt(data, p4)

            cx += dx
            cy += dy
            cz += dz

            entries += ChangeEntry(cx, cy, cz, from.toShort(), to.toShort())
            pos = p5
        }

        return entries
    }

    private fun writeVarInt(value: Int) {
        var v = (value shl 1) xor (value shr 31)
        while (v and 0x7F.inv() != 0) {
            stream.write((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        stream.write(v)
    }

    private fun readVarInt(data: ByteArray, offset: Int): Pair<Int, Int> {
        var value = 0
        var shift = 0
        var pos = offset
        var b: Int
        do {
            b = data[pos++].toInt() and 0xFF
            value = value or ((b and 0x7F) shl shift)
            shift += 7
        } while (b and 0x80 != 0)
        val decoded = (value ushr 1) xor -(value and 1)
        return decoded to pos
    }

    private data class ChangeEntry(val x: Int, val y: Int, val z: Int, val fromOrdinal: Short, val toOrdinal: Short)
}
