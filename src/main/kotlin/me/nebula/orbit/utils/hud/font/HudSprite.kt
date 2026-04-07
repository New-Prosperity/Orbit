package me.nebula.orbit.utils.hud.font

import java.awt.Color
import java.awt.image.BufferedImage

val HEIGHT_TIERS = intArrayOf(8, 16, 24, 32, 48, 64, 96, 128)

fun heightTierIndex(pixelHeight: Int): Int {
    for (i in HEIGHT_TIERS.indices) {
        if (HEIGHT_TIERS[i] >= pixelHeight) return i
    }
    return HEIGHT_TIERS.lastIndex
}

data class HudSpriteColumn(
    val char: Char,
    val columnIndex: Int,
    val atlasRow: Int,
    val atlasCol: Int,
)

data class HudSpriteDef(
    val id: String,
    val tierIndex: Int,
    val tierHeight: Int,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val columns: List<HudSpriteColumn>,
    val image: BufferedImage,
)

object HudSpriteRegistry {

    const val CELL_WIDTH = 8
    const val ATLAS_COLUMNS = 32

    private val sprites = LinkedHashMap<String, HudSpriteDef>()
    private var nextChar = '\uE100'

    private val tierCurrentRow = IntArray(HEIGHT_TIERS.size)
    private val tierCurrentCol = IntArray(HEIGHT_TIERS.size)

    fun register(id: String, image: BufferedImage): HudSpriteDef {
        require(id !in sprites) { "Sprite already registered: $id" }
        val w = image.width
        val h = image.height
        val tier = heightTierIndex(h)
        val tierH = HEIGHT_TIERS[tier]
        val columnCount = ((w + CELL_WIDTH - 1) / CELL_WIDTH).coerceAtLeast(1)

        val cols = mutableListOf<HudSpriteColumn>()
        for (c in 0 until columnCount) {
            val ch = nextChar++
            val row = tierCurrentRow[tier]
            val col = tierCurrentCol[tier]
            cols += HudSpriteColumn(ch, c, row, col)
            tierCurrentCol[tier]++
            if (tierCurrentCol[tier] >= ATLAS_COLUMNS) {
                tierCurrentCol[tier] = 0
                tierCurrentRow[tier]++
            }
        }

        val def = HudSpriteDef(id, tier, tierH, w, h, cols, image)
        sprites[id] = def
        return def
    }

    fun register(id: String, width: Int, height: Int, color: Color): HudSpriteDef {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        return register(id, img)
    }

    fun get(id: String): HudSpriteDef = sprites.getValue(id)
    fun getOrNull(id: String): HudSpriteDef? = sprites[id]
    fun all(): Collection<HudSpriteDef> = sprites.values

    fun tierRowCount(tierIndex: Int): Int {
        val col = tierCurrentCol[tierIndex]
        val row = tierCurrentRow[tierIndex]
        return if (col > 0) row + 1 else row
    }

    fun spritesForTier(tierIndex: Int): List<HudSpriteDef> =
        sprites.values.filter { it.tierIndex == tierIndex }

    fun firstChar(spriteId: String): Char =
        get(spriteId).columns.first().char

    fun columnCount(spriteId: String): Int =
        get(spriteId).columns.size

    init {
        register("bar_bg", CELL_WIDTH, CELL_WIDTH, Color(60, 60, 60))
        register("bar_fill_red", CELL_WIDTH, CELL_WIDTH, Color(220, 50, 50))
        register("bar_fill_blue", CELL_WIDTH, CELL_WIDTH, Color(50, 100, 220))
        register("bar_fill_green", CELL_WIDTH, CELL_WIDTH, Color(50, 200, 50))
        register("bar_fill_yellow", CELL_WIDTH, CELL_WIDTH, Color(230, 220, 50))
        register("bar_empty", CELL_WIDTH, CELL_WIDTH, Color(40, 40, 40))

        register("icon_health", CELL_WIDTH, CELL_WIDTH, Color(220, 30, 30))
        register("icon_mana", CELL_WIDTH, CELL_WIDTH, Color(50, 80, 220))
        register("icon_shield", CELL_WIDTH, CELL_WIDTH, Color(180, 180, 200))
        register("icon_speed", CELL_WIDTH, CELL_WIDTH, Color(100, 200, 230))
        register("icon_strength", CELL_WIDTH, CELL_WIDTH, Color(180, 40, 40))
        register("icon_fire", CELL_WIDTH, CELL_WIDTH, Color(240, 150, 30))

        for (i in 0..9) register("digit_$i", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
        register("glyph_colon", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
        register("glyph_slash", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
        register("glyph_dot", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
        register("glyph_percent", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
        register("glyph_dash", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
        register("glyph_underscore", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
        register("glyph_arrow", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))

        for (ch in 'a'..'z') register("letter_$ch", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
        for (ch in 'A'..'Z') register("letter_$ch", CELL_WIDTH, CELL_WIDTH, Color(0, 0, 0, 0))
    }
}
