package me.nebula.orbit.utils.nebulaworld

import kotlin.math.ceil
import kotlin.math.max

object PaletteUtil {

    fun bitsPerEntry(paletteSize: Int): Int =
        max(1, ceil(Math.log(paletteSize.toDouble()) / Math.log(2.0)).toInt())

    fun pack(indices: IntArray, bitsPerEntry: Int): LongArray {
        val entriesPerLong = 64 / bitsPerEntry
        val longCount = ceil(indices.size.toDouble() / entriesPerLong).toInt()
        val longs = LongArray(longCount)
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
}
