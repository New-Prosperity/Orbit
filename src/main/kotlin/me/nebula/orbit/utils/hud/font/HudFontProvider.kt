package me.nebula.orbit.utils.hud.font

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.awt.Color
import java.awt.Graphics2D
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.awt.image.BufferedImage

object HudFontProvider {

    private const val CELL_SIZE = 8
    private val gson = GsonBuilder().setPrettyPrinting().create()

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

    private const val BITMAP_WIDTH = 5

    fun generate(): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        entries["assets/nebula/textures/hud/sprites.png"] = generateAtlas()
        entries["assets/minecraft/font/hud.json"] = generateFontJson()
        return entries
    }

    private fun generateAtlas(): ByteArray {
        val columns = HudSpriteRegistry.COLUMNS
        val rows = HudSpriteRegistry.rowCount()
        if (rows == 0) return ByteArray(0)

        val image = BufferedImage(columns * CELL_SIZE, rows * CELL_SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()

        for (sprite in HudSpriteRegistry.all()) {
            val x = sprite.col * CELL_SIZE
            val y = sprite.row * CELL_SIZE
            val custom = HudSpriteRegistry.customImage(sprite.id)
            if (custom != null) {
                g.drawImage(custom, x, y, CELL_SIZE, CELL_SIZE, null)
            } else {
                drawDefaultSprite(g, sprite.id, x, y)
            }
        }

        g.dispose()
        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "png", baos)
        return baos.toByteArray()
    }

    private fun drawDefaultSprite(g: Graphics2D, id: String, x: Int, y: Int) {
        when {
            id.startsWith("digit_") -> {
                val digit = id.removePrefix("digit_").toInt()
                drawBitmap(g, DIGIT_PATTERNS[digit], x, y, Color.WHITE)
            }
            id.startsWith("glyph_") -> {
                val pattern = GLYPH_PATTERNS[id] ?: return
                drawBitmap(g, pattern, x, y, Color.WHITE)
            }
            else -> {
                g.color = spriteColor(id)
                g.fillRect(x, y, CELL_SIZE, CELL_SIZE)
            }
        }
    }

    private fun drawBitmap(g: Graphics2D, pattern: IntArray, ox: Int, oy: Int, color: Color) {
        g.color = color
        val padX = (CELL_SIZE - BITMAP_WIDTH) / 2
        val padY = (CELL_SIZE - pattern.size) / 2
        for (row in pattern.indices) {
            for (col in 0 until BITMAP_WIDTH) {
                if (pattern[row] and (1 shl (BITMAP_WIDTH - 1 - col)) != 0) {
                    g.fillRect(ox + padX + col, oy + padY + row, 1, 1)
                }
            }
        }
    }

    @Suppress("MagicNumber")
    private fun spriteColor(id: String): Color = when (id) {
        "bar_bg" -> Color(60, 60, 60)
        "bar_fill_red" -> Color(220, 50, 50)
        "bar_fill_blue" -> Color(50, 100, 220)
        "bar_fill_green" -> Color(50, 200, 50)
        "bar_fill_yellow" -> Color(230, 220, 50)
        "bar_empty" -> Color(40, 40, 40)
        "icon_health" -> Color(220, 30, 30)
        "icon_mana" -> Color(50, 80, 220)
        "icon_shield" -> Color(180, 180, 200)
        "icon_speed" -> Color(100, 200, 230)
        "icon_strength" -> Color(180, 40, 40)
        "icon_fire" -> Color(240, 150, 30)
        else -> Color(200, 200, 200)
    }

    private fun generateFontJson(): ByteArray {
        val allSprites = HudSpriteRegistry.all().toList()
        val columns = HudSpriteRegistry.COLUMNS
        val rows = HudSpriteRegistry.rowCount()

        val charRows = (0 until rows).map { row ->
            buildString {
                for (col in 0 until columns) {
                    val sprite = allSprites.firstOrNull { it.row == row && it.col == col }
                    append(sprite?.char ?: '\u0000')
                }
            }
        }

        val json = JsonObject().apply {
            add("providers", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("type", "bitmap")
                    addProperty("file", "nebula:hud/sprites.png")
                    addProperty("height", CELL_SIZE)
                    addProperty("ascent", CELL_SIZE)
                    add("chars", JsonArray().apply {
                        charRows.forEach { add(it) }
                    })
                })
            })
        }
        return gson.toJson(json).toByteArray(Charsets.UTF_8)
    }
}
