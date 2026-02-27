package me.nebula.orbit.utils.modelengine.generator

import me.nebula.ether.utils.logging.logger
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO

data class AtlasEntry(
    val texture: BbTexture,
    val image: BufferedImage,
    val offsetX: Int,
    val offsetY: Int,
    val originalIndex: Int,
)

data class AtlasResult(
    val image: BufferedImage,
    val width: Int,
    val height: Int,
    val entries: List<AtlasEntry>,
)

object AtlasManager {

    private val logger = logger("AtlasManager")

    fun stitch(textures: List<BbTexture>, cropTo: BbResolution? = null): AtlasResult {
        val images = textures.mapIndexedNotNull { index, tex ->
            val decoded = decodeBase64Image(tex.source)
            if (decoded == null) {
                logger.warn { "Failed to decode texture ${tex.name} (index=$index, source=${tex.source.take(40)}...)" }
                return@mapIndexedNotNull null
            }
            val imageData = if (cropTo != null) {
                val cropW = minOf(decoded.width, cropTo.width)
                val cropH = minOf(decoded.height, cropTo.height)
                if (cropW < decoded.width || cropH < decoded.height) decoded.getSubimage(0, 0, cropW, cropH)
                else decoded
            } else decoded
            Triple(tex, imageData, index)
        }

        if (images.isEmpty()) {
            logger.warn { "No valid textures decoded, returning empty atlas" }
            val empty = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            return AtlasResult(empty, 16, 16, emptyList())
        }

        if (images.size == 1) {
            val (tex, img, idx) = images[0]
            val entry = AtlasEntry(tex, img, 0, 0, idx)
            return AtlasResult(img, img.width, img.height, listOf(entry))
        }

        val totalArea = images.sumOf { it.second.width * it.second.height }
        var atlasWidth = nextPowerOf2(kotlin.math.sqrt(totalArea.toDouble()).toInt())
        var atlasHeight = atlasWidth

        val entries = mutableListOf<AtlasEntry>()
        var currentX = 0
        var currentY = 0
        var rowHeight = 0

        for ((tex, img, idx) in images) {
            if (currentX + img.width > atlasWidth) {
                currentX = 0
                currentY += rowHeight
                rowHeight = 0
            }
            if (currentY + img.height > atlasHeight) {
                atlasHeight = nextPowerOf2(currentY + img.height)
            }
            entries += AtlasEntry(tex, img, currentX, currentY, idx)
            currentX += img.width
            rowHeight = maxOf(rowHeight, img.height)
        }

        atlasHeight = nextPowerOf2(currentY + rowHeight)

        val atlas = BufferedImage(atlasWidth, atlasHeight, BufferedImage.TYPE_INT_ARGB)
        val g = atlas.createGraphics()
        entries.forEach { entry ->
            g.drawImage(entry.image, entry.offsetX, entry.offsetY, null)
        }
        g.dispose()

        return AtlasResult(atlas, atlasWidth, atlasHeight, entries)
    }

    fun toBytes(image: BufferedImage): ByteArray {
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    fun entryByOriginalIndex(entries: List<AtlasEntry>, index: Int): AtlasEntry? =
        entries.firstOrNull { it.originalIndex == index }

    private fun decodeBase64Image(source: String): BufferedImage? = runCatching {
        if (source.isBlank()) return null
        val data = if (source.contains(",")) source.substringAfter(",") else source
        val bytes = Base64.getDecoder().decode(data)
        ImageIO.read(ByteArrayInputStream(bytes))
    }.onFailure { logger.warn { "Base64 image decode error: ${it.message}" } }.getOrNull()

    private fun nextPowerOf2(n: Int): Int {
        if (n <= 0) return 1
        var v = n - 1
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        return v + 1
    }
}
