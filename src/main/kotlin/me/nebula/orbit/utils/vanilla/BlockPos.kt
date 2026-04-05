package me.nebula.orbit.utils.vanilla

fun packBlockPos(x: Int, y: Int, z: Int): Long =
    (x.toLong() shl 38) or ((z.toLong() and 0x3FFFFFFL) shl 12) or (y.toLong() and 0xFFFL)

fun unpackBlockX(packed: Long): Int = (packed shr 38).toInt()

fun unpackBlockZ(packed: Long): Int {
    val raw = ((packed shr 12) and 0x3FFFFFFL).toInt()
    return if (raw and 0x2000000 != 0) raw or ((-1) shl 26) else raw
}

fun unpackBlockY(packed: Long): Int {
    val raw = (packed and 0xFFFL).toInt()
    return if (raw and 0x800 != 0) raw or ((-1) shl 12) else raw
}
