package me.nebula.orbit.utils.screen.texture

import me.nebula.orbit.utils.screen.canvas.MapCanvas
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

data class Texture(val pixels: IntArray, val width: Int, val height: Int) {

    fun scaled(newWidth: Int, newHeight: Int): Texture {
        require(newWidth > 0 && newHeight > 0) { "Scaled dimensions must be positive" }
        val result = IntArray(newWidth * newHeight)
        val xRatio = width.toDouble() / newWidth
        val yRatio = height.toDouble() / newHeight
        for (y in 0 until newHeight) {
            val srcY = (y * yRatio).toInt().coerceAtMost(height - 1)
            val srcRow = srcY * width
            val dstRow = y * newWidth
            for (x in 0 until newWidth) {
                val srcX = (x * xRatio).toInt().coerceAtMost(width - 1)
                result[dstRow + x] = pixels[srcRow + srcX]
            }
        }
        return Texture(result, newWidth, newHeight)
    }

    fun subRegion(x: Int, y: Int, w: Int, h: Int): Texture {
        require(x >= 0 && y >= 0 && x + w <= width && y + h <= height) { "Sub-region out of bounds" }
        val result = IntArray(w * h)
        for (ry in 0 until h) {
            System.arraycopy(pixels, (y + ry) * width + x, result, ry * w, w)
        }
        return Texture(result, w, h)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Texture) return false
        return width == other.width && height == other.height && pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int = 31 * (31 * width + height) + pixels.contentHashCode()
}

object TextureLoader {

    fun fromClasspath(path: String): Texture {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("Classpath resource not found: $path")
        return stream.use { fromBufferedImage(ImageIO.read(it)) }
    }

    fun fromBytes(bytes: ByteArray): Texture =
        fromBufferedImage(ImageIO.read(ByteArrayInputStream(bytes)))

    fun fromBufferedImage(image: BufferedImage): Texture {
        val w = image.width
        val h = image.height
        val pixels = IntArray(w * h)
        image.getRGB(0, 0, w, h, pixels, 0, w)
        return Texture(pixels, w, h)
    }
}

fun MapCanvas.drawTexture(x: Int, y: Int, texture: Texture) {
    drawImage(x, y, texture.pixels, texture.width, texture.height)
}
