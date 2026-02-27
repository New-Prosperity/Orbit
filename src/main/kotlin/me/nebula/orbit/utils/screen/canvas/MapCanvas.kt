package me.nebula.orbit.utils.screen.canvas

import me.nebula.orbit.utils.screen.encoder.TILE_PIXELS
import java.util.BitSet

class MapCanvas(val width: Int, val height: Int) {

    val pixels = IntArray(width * height)

    private val tilesX = width / TILE_PIXELS
    private val tilesY = height / TILE_PIXELS
    private val dirtyTiles = BitSet(tilesX * tilesY)

    fun pixel(x: Int, y: Int, color: Int) {
        if (x in 0 until width && y in 0 until height) {
            pixels[y * width + x] = color
            markDirty(x, y)
        }
    }

    fun get(x: Int, y: Int): Int =
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] else 0

    fun clear(color: Int = 0) {
        pixels.fill(color)
        dirtyTiles.set(0, dirtyTiles.size())
    }

    fun fill(x: Int, y: Int, w: Int, h: Int, color: Int) {
        val x0 = x.coerceIn(0, width)
        val y0 = y.coerceIn(0, height)
        val x1 = (x + w).coerceIn(0, width)
        val y1 = (y + h).coerceIn(0, height)
        if (x0 >= x1 || y0 >= y1) return
        for (py in y0 until y1) {
            java.util.Arrays.fill(pixels, py * width + x0, py * width + x1, color)
        }
        markDirtyRegion(x0, y0, x1, y1)
    }

    fun drawImage(x: Int, y: Int, source: IntArray, srcWidth: Int, srcHeight: Int) {
        val x0 = x.coerceAtLeast(0)
        val y0 = y.coerceAtLeast(0)
        val x1 = (x + srcWidth).coerceAtMost(width)
        val y1 = (y + srcHeight).coerceAtMost(height)
        for (sy in 0 until srcHeight) {
            val dy = y + sy
            if (dy < 0 || dy >= height) continue
            val srcRow = sy * srcWidth
            val dstRow = dy * width
            for (sx in 0 until srcWidth) {
                val dx = x + sx
                if (dx < 0 || dx >= width) continue
                val srcPixel = source[srcRow + sx]
                if (srcPixel ushr 24 != 0) {
                    pixels[dstRow + dx] = srcPixel
                }
            }
        }
        markDirtyRegion(x0, y0, x1, y1)
    }

    fun getRegion(x: Int, y: Int, w: Int, h: Int): IntArray {
        val result = IntArray(w * h)
        for (ry in 0 until h) {
            val sy = y + ry
            if (sy < 0 || sy >= height) continue
            val srcRow = sy * width
            val dstRow = ry * w
            for (rx in 0 until w) {
                val sx = x + rx
                if (sx < 0 || sx >= width) continue
                result[dstRow + rx] = pixels[srcRow + sx]
            }
        }
        return result
    }

    fun isDirty(tileX: Int, tileY: Int): Boolean =
        dirtyTiles[tileY * tilesX + tileX]

    fun clearDirty() {
        dirtyTiles.clear()
    }

    fun markAllDirty() {
        dirtyTiles.set(0, tilesX * tilesY)
    }

    private fun markDirty(px: Int, py: Int) {
        dirtyTiles.set((py / TILE_PIXELS) * tilesX + (px / TILE_PIXELS))
    }

    internal fun markDirtyRegion(x0: Int, y0: Int, x1: Int, y1: Int) {
        if (x0 >= x1 || y0 >= y1) return
        val tx0 = x0 / TILE_PIXELS
        val ty0 = y0 / TILE_PIXELS
        val tx1 = ((x1 - 1) / TILE_PIXELS).coerceAtMost(tilesX - 1)
        val ty1 = ((y1 - 1) / TILE_PIXELS).coerceAtMost(tilesY - 1)
        for (ty in ty0..ty1) {
            for (tx in tx0..tx1) {
                dirtyTiles.set(ty * tilesX + tx)
            }
        }
    }
}
