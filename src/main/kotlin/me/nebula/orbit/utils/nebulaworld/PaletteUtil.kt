package me.nebula.orbit.utils.nebulaworld

import kotlin.math.max

object PaletteUtil {

    fun bitsPerEntry(paletteSize: Int): Int {
        if (paletteSize <= 1) return 0
        var bits = 0
        var n = paletteSize - 1
        while (n > 0) {
            bits++
            n = n ushr 1
        }
        return max(1, bits)
    }

    fun longCount(entryCount: Int, bitsPerEntry: Int): Int {
        if (bitsPerEntry == 0) return 0
        val entriesPerLong = 64 / bitsPerEntry
        return (entryCount + entriesPerLong - 1) / entriesPerLong
    }

    fun pack(indices: IntArray, bitsPerEntry: Int): LongArray {
        if (bitsPerEntry == 0) return LongArray(0)
        val entriesPerLong = 64 / bitsPerEntry
        val longs = LongArray(longCount(indices.size, bitsPerEntry))
        val mask = (1L shl bitsPerEntry) - 1

        for (i in indices.indices) {
            val longIndex = i / entriesPerLong
            val subIndex = i % entriesPerLong
            longs[longIndex] = longs[longIndex] or ((indices[i].toLong() and mask) shl (bitsPerEntry * subIndex))
        }
        return longs
    }

    fun unpack(longs: LongArray, bitsPerEntry: Int, count: Int): IntArray {
        val result = IntArray(count)
        if (bitsPerEntry == 0) return result
        val entriesPerLong = 64 / bitsPerEntry
        val mask = (1L shl bitsPerEntry) - 1

        for (i in 0 until count) {
            val longIndex = i / entriesPerLong
            val subIndex = i % entriesPerLong
            if (longIndex < longs.size) {
                result[i] = ((longs[longIndex] ushr (bitsPerEntry * subIndex)) and mask).toInt()
            }
        }
        return result
    }

    fun zigzagEncode(v: Int): Int = (v shl 1) xor (v shr 31)
    fun zigzagDecode(v: Int): Int = (v ushr 1) xor -(v and 1)
}
