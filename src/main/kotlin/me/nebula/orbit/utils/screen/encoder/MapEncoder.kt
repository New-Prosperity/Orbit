package me.nebula.orbit.utils.screen.encoder

import me.nebula.orbit.utils.screen.canvas.MapCanvas

const val TILE_PIXELS = 64
const val MAP_SIZE = 128

val MAGIC_BYTES = intArrayOf(7, 42, 99, 126)

data class EncodedChunk(
    val gridX: Int,
    val gridY: Int,
    val data: ByteArray,
    val hash: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedChunk) return false
        return gridX == other.gridX && gridY == other.gridY && hash == other.hash
    }

    override fun hashCode(): Int = 31 * (31 * gridX + gridY) + hash.hashCode()
}

object MapEncoder {

    fun encodeCanvas(canvas: MapCanvas, dirtyOnly: Boolean = false): List<EncodedChunk> {
        val tilesX = canvas.width / TILE_PIXELS
        val tilesY = canvas.height / TILE_PIXELS
        val chunks = ArrayList<EncodedChunk>(tilesX * tilesY)
        for (ty in 0 until tilesY) {
            for (tx in 0 until tilesX) {
                if (dirtyOnly && !canvas.isDirty(tx, ty)) continue
                val data = encodeTileDirect(canvas, tx, ty, tx == 0 && ty == 0)
                val hash = contentHash(data)
                chunks += EncodedChunk(tx, ty, data, hash)
            }
        }
        return chunks
    }

    fun encodeTileDirect(canvas: MapCanvas, tileX: Int, tileY: Int, writeMagic: Boolean): ByteArray {
        val data = ByteArray(MAP_SIZE * MAP_SIZE)
        val startX = tileX * TILE_PIXELS
        val startY = tileY * TILE_PIXELS
        val canvasW = canvas.width
        val pixels = canvas.pixels
        for (py in 0 until TILE_PIXELS) {
            val srcRow = (startY + py) * canvasW + startX
            for (px in 0 until TILE_PIXELS) {
                val argb = pixels[srcRow + px]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val mx = px * 2
                val my = py * 2
                data[my * MAP_SIZE + mx] = ((b and 0x7F) + 4).toByte()
                data[my * MAP_SIZE + mx + 1] = ((g and 0x7F) + 4).toByte()
                data[(my + 1) * MAP_SIZE + mx] = ((r and 0x7F) + 4).toByte()
                val msb = ((r shr 7) shl 2) or ((g shr 7) shl 1) or (b shr 7)
                data[(my + 1) * MAP_SIZE + mx + 1] = (msb + 4).toByte()
            }
        }
        if (writeMagic) {
            data[0] = MAGIC_BYTES[0].toByte()
            data[1] = MAGIC_BYTES[1].toByte()
            data[MAP_SIZE] = MAGIC_BYTES[2].toByte()
            data[MAP_SIZE + 1] = MAGIC_BYTES[3].toByte()
        }
        return data
    }

    fun encodeTile(pixels: IntArray, writeMagic: Boolean): ByteArray {
        require(pixels.size == TILE_PIXELS * TILE_PIXELS) { "Tile must be ${TILE_PIXELS}x${TILE_PIXELS}" }
        val data = ByteArray(MAP_SIZE * MAP_SIZE)
        for (py in 0 until TILE_PIXELS) {
            for (px in 0 until TILE_PIXELS) {
                val argb = pixels[py * TILE_PIXELS + px]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF

                val mx = px * 2
                val my = py * 2

                data[my * MAP_SIZE + mx] = ((b and 0x7F) + 4).toByte()
                data[my * MAP_SIZE + mx + 1] = ((g and 0x7F) + 4).toByte()
                data[(my + 1) * MAP_SIZE + mx] = ((r and 0x7F) + 4).toByte()
                val msb = ((r shr 7) shl 2) or ((g shr 7) shl 1) or (b shr 7)
                data[(my + 1) * MAP_SIZE + mx + 1] = (msb + 4).toByte()
            }
        }
        if (writeMagic) {
            data[0] = MAGIC_BYTES[0].toByte()
            data[1] = MAGIC_BYTES[1].toByte()
            data[MAP_SIZE] = MAGIC_BYTES[2].toByte()
            data[MAP_SIZE + 1] = MAGIC_BYTES[3].toByte()
        }
        return data
    }

    fun contentHash(data: ByteArray): Long {
        var h = 0L
        for (i in data.indices) {
            h = h * 31 + (data[i].toLong() and 0xFF)
        }
        return h
    }
}
