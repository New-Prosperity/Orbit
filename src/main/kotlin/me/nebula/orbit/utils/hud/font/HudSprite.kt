package me.nebula.orbit.utils.hud.font

import java.awt.image.BufferedImage

data class HudSpriteDefinition(
    val id: String,
    val char: Char,
    val row: Int,
    val col: Int,
)

object HudSpriteRegistry {

    const val COLUMNS = 16

    private val sprites = LinkedHashMap<String, HudSpriteDefinition>()
    private val customImages = LinkedHashMap<String, BufferedImage>()
    private var nextChar = '\uE000'
    private var currentRow = 0
    private var currentCol = 0

    fun register(id: String): HudSpriteDefinition {
        require(id !in sprites) { "Sprite already registered: $id" }
        val def = HudSpriteDefinition(id, nextChar, currentRow, currentCol)
        sprites[id] = def
        nextChar++
        currentCol++
        if (currentCol >= COLUMNS) {
            currentCol = 0
            currentRow++
        }
        return def
    }

    fun registerFromImage(id: String, image: BufferedImage): HudSpriteDefinition {
        val def = register(id)
        customImages[id] = image
        return def
    }

    fun get(id: String): HudSpriteDefinition = sprites.getValue(id)
    fun getOrNull(id: String): HudSpriteDefinition? = sprites[id]
    fun all(): Collection<HudSpriteDefinition> = sprites.values
    fun charFor(id: String): Char = get(id).char
    fun customImage(id: String): BufferedImage? = customImages[id]
    fun rowCount(): Int = if (sprites.isEmpty()) 0 else currentRow + if (currentCol > 0) 1 else 0

    init {
        register("bar_bg")
        register("bar_fill_red")
        register("bar_fill_blue")
        register("bar_fill_green")
        register("bar_fill_yellow")
        register("bar_empty")

        register("icon_health")
        register("icon_mana")
        register("icon_shield")
        register("icon_speed")
        register("icon_strength")
        register("icon_fire")

        register("border_left")
        register("border_right")
        register("border_top")
        register("border_bottom")
        register("corner_tl")
        register("corner_tr")
        register("corner_bl")
        register("corner_br")

        for (i in 0..9) register("digit_$i")
        register("glyph_colon")
        register("glyph_slash")
        register("glyph_dot")
        register("glyph_percent")
    }
}
