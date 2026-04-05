package me.nebula.orbit.utils.hud.font

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.awt.Color
import java.awt.Graphics2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

object HudFontProvider {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    private const val BITMAP_WIDTH = 5

    @Suppress("MagicNumber")
    private val DIGIT_PATTERNS = arrayOf(
        intArrayOf(0b01110, 0b10001, 0b10011, 0b10101, 0b11001, 0b10001, 0b01110),
        intArrayOf(0b00100, 0b01100, 0b00100, 0b00100, 0b00100, 0b00100, 0b01110),
        intArrayOf(0b01110, 0b10001, 0b00001, 0b00010, 0b00100, 0b01000, 0b11111),
        intArrayOf(0b01110, 0b10001, 0b00001, 0b00110, 0b00001, 0b10001, 0b01110),
        intArrayOf(0b00010, 0b00110, 0b01010, 0b10010, 0b11111, 0b00010, 0b00010),
        intArrayOf(0b11111, 0b10000, 0b11110, 0b00001, 0b00001, 0b10001, 0b01110),
        intArrayOf(0b00110, 0b01000, 0b10000, 0b11110, 0b10001, 0b10001, 0b01110),
        intArrayOf(0b11111, 0b00001, 0b00010, 0b00100, 0b01000, 0b01000, 0b01000),
        intArrayOf(0b01110, 0b10001, 0b10001, 0b01110, 0b10001, 0b10001, 0b01110),
        intArrayOf(0b01110, 0b10001, 0b10001, 0b01111, 0b00001, 0b00010, 0b01100),
    )

    @Suppress("MagicNumber")
    private val GLYPH_PATTERNS = mapOf(
        "glyph_colon" to intArrayOf(0b00000, 0b00100, 0b00000, 0b00000, 0b00100, 0b00000, 0b00000),
        "glyph_slash" to intArrayOf(0b00001, 0b00010, 0b00100, 0b01000, 0b10000, 0b00000, 0b00000),
        "glyph_dot" to intArrayOf(0b00000, 0b00000, 0b00000, 0b00000, 0b00000, 0b00100, 0b00000),
        "glyph_percent" to intArrayOf(0b10001, 0b00010, 0b00100, 0b01000, 0b10001, 0b00000, 0b00000),
    )

    fun generate(): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()

        for ((tierIndex, tierHeight) in HEIGHT_TIERS.withIndex()) {
            val tierSprites = HudSpriteRegistry.spritesForTier(tierIndex)
            if (tierSprites.isEmpty()) continue
            val atlasBytes = generateTierAtlas(tierIndex, tierHeight, tierSprites)
            entries["assets/nebula/textures/hud/tier_$tierIndex.png"] = atlasBytes
        }

        entries["assets/minecraft/font/hud.json"] = generateFontJson()
        return entries
    }

    private fun generateTierAtlas(tierIndex: Int, tierHeight: Int, sprites: List<HudSpriteDef>): ByteArray {
        val rows = HudSpriteRegistry.tierRowCount(tierIndex)
        if (rows == 0) return ByteArray(0)

        val atlasW = HudSpriteRegistry.ATLAS_COLUMNS * HudSpriteRegistry.CELL_WIDTH
        val atlasH = rows * tierHeight
        val image = BufferedImage(atlasW, atlasH, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        for (sprite in sprites) {
            for (col in sprite.columns) {
                val destX = col.atlasCol * HudSpriteRegistry.CELL_WIDTH
                val destY = col.atlasRow * tierHeight
                val srcX = col.columnIndex * HudSpriteRegistry.CELL_WIDTH
                val srcW = minOf(HudSpriteRegistry.CELL_WIDTH, sprite.sourceWidth - srcX)
                val srcH = minOf(tierHeight, sprite.sourceHeight)

                if (srcW > 0 && srcH > 0) {
                    val sub = sprite.image.getSubimage(srcX, 0, srcW, srcH)
                    g.drawImage(sub, destX, destY, null)
                }

                if (isDefaultSprite(sprite.id) && col.columnIndex == 0) {
                    drawDefaultSprite(g, sprite.id, destX, destY, tierHeight)
                }
            }
        }

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    private fun isDefaultSprite(id: String): Boolean =
        id.startsWith("digit_") || id.startsWith("glyph_")

    private fun drawDefaultSprite(g: Graphics2D, id: String, x: Int, y: Int, cellHeight: Int) {
        when {
            id.startsWith("digit_") -> {
                val digit = id.removePrefix("digit_").toInt()
                drawBitmap(g, DIGIT_PATTERNS[digit], x, y, cellHeight, Color.WHITE)
            }
            id.startsWith("glyph_") -> {
                val pattern = GLYPH_PATTERNS[id] ?: return
                drawBitmap(g, pattern, x, y, cellHeight, Color.WHITE)
            }
        }
    }

    private fun drawBitmap(g: Graphics2D, pattern: IntArray, ox: Int, oy: Int, cellHeight: Int, color: Color) {
        g.color = color
        val padX = (HudSpriteRegistry.CELL_WIDTH - BITMAP_WIDTH) / 2
        val padY = (cellHeight - pattern.size) / 2
        for (row in pattern.indices) {
            for (col in 0 until BITMAP_WIDTH) {
                if (pattern[row] and (1 shl (BITMAP_WIDTH - 1 - col)) != 0) {
                    g.fillRect(ox + padX + col, oy + padY + row, 1, 1)
                }
            }
        }
    }

    private fun generateFontJson(): ByteArray {
        val json = JsonObject().apply {
            add("providers", JsonArray().apply {
                for ((tierIndex, tierHeight) in HEIGHT_TIERS.withIndex()) {
                    val tierSprites = HudSpriteRegistry.spritesForTier(tierIndex)
                    if (tierSprites.isEmpty()) continue

                    val rows = HudSpriteRegistry.tierRowCount(tierIndex)
                    val allColumns = tierSprites.flatMap { it.columns }

                    val charRows = (0 until rows).map { row ->
                        buildString {
                            for (col in 0 until HudSpriteRegistry.ATLAS_COLUMNS) {
                                val column = allColumns.firstOrNull { it.atlasRow == row && it.atlasCol == col }
                                append(column?.char ?: '\u0000')
                            }
                        }
                    }

                    add(JsonObject().apply {
                        addProperty("type", "bitmap")
                        addProperty("file", "nebula:hud/tier_$tierIndex.png")
                        addProperty("height", tierHeight)
                        addProperty("ascent", tierHeight)
                        add("chars", JsonArray().apply {
                            charRows.forEach { add(it) }
                        })
                    })
                }
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }
}
