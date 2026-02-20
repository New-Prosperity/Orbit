package me.nebula.orbit.utils.modelengine.generator

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

    fun stitch(textures: List<BbTexture>): AtlasResult {
        val images = textures.mapIndexed { index, tex ->
            val imageData = decodeBase64Image(tex.source)
                ?: return AtlasResult(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), 16, 16, emptyList())
            Triple(tex, imageData, index)
        }

        if (images.isEmpty()) {
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

    private fun decodeBase64Image(source: String): BufferedImage? {
        val data = if (source.contains(",")) {
            source.substringAfter(",")
        } else source
        val bytes = Base64.getDecoder().decode(data)
        return ImageIO.read(ByteArrayInputStream(bytes))
    }

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
