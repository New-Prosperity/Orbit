package me.nebula.orbit.utils.hud

import me.nebula.orbit.utils.hud.font.HudSpriteRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.TextColor

object HudRenderer {

    val HUD_FONT: Key = Key.key("minecraft", "hud")

    fun render(hud: PlayerHud): Component {
        val parts = mutableListOf<Component>()

        for (element in hud.layout.elements) {
            if (!hud.isElementVisible(element.id)) continue
            when (element) {
                is SpriteElement -> renderSprite(parts, element)
                is BarElement -> renderBar(parts, element, hud)
                is TextElement -> renderText(parts, element, hud)
                is GroupElement -> renderGroup(parts, element, hud)
                is AnimatedSpriteElement -> renderAnimated(parts, element, hud)
            }
        }

        return Component.join(JoinConfiguration.noSeparators(), parts)
    }

    private fun computePosition(element: HudElement): Pair<Float, Float> {
        val absX = (element.anchor.nx + element.offsetX).coerceIn(0f, 1f)
        val absY = (element.anchor.ny + element.offsetY).coerceIn(0f, 1f)
        return absX to absY
    }

    private fun encodeColor(x: Float, y: Float, tierIndex: Int, charOffset: Int): TextColor {
        val b = 128 + (tierIndex.coerceIn(0, 7) shl 4) + charOffset.coerceIn(0, 15)
        return TextColor.color(
            (x * 255).toInt().coerceIn(0, 255),
            (y * 255).toInt().coerceIn(0, 255),
            b,
        )
    }

    private fun spriteColumnComponent(spriteId: String, columnIndex: Int, x: Float, y: Float, charOffset: Int): Component {
        val sprite = HudSpriteRegistry.get(spriteId)
        val ch = sprite.columns[columnIndex].char
        return Component.text(ch.toString())
            .color(encodeColor(x, y, sprite.tierIndex, charOffset))
            .font(HUD_FONT)
    }

    private fun emitSprite(parts: MutableList<Component>, spriteId: String, x: Float, y: Float, baseOffset: Int) {
        val sprite = HudSpriteRegistry.get(spriteId)
        for (col in sprite.columns) {
            parts += Component.text(col.char.toString())
                .color(encodeColor(x, y, sprite.tierIndex, baseOffset + col.columnIndex))
                .font(HUD_FONT)
        }
    }

    private fun renderSprite(parts: MutableList<Component>, element: SpriteElement) {
        val (x, y) = computePosition(element)
        emitSprite(parts, element.spriteId, x, y, 0)
    }

    private fun renderBar(parts: MutableList<Component>, element: BarElement, hud: PlayerHud) {
        val (baseX, baseY) = computePosition(element)
        val value = (hud.values[element.id] as? Number)?.toInt() ?: 0
        val filled = value.coerceIn(0, element.segments)

        val bgCols = HudSpriteRegistry.columnCount(element.bgSprite)
        emitSprite(parts, element.bgSprite, baseX, baseY, 0)

        for (i in 0 until element.segments) {
            val sprite = if (i < filled) element.fillSprite else element.emptySprite
            val segCols = HudSpriteRegistry.columnCount(sprite)
            val offset = bgCols + i * segCols
            emitSprite(parts, sprite, baseX, baseY, offset)
        }
    }

    private fun renderText(parts: MutableList<Component>, element: TextElement, hud: PlayerHud) {
        val (baseX, baseY) = computePosition(element)
        val text = hud.values[element.id]?.toString() ?: return

        var offset = 0
        var i = 0
        while (i < text.length) {
            if (text[i] == '{') {
                val end = text.indexOf('}', i + 1)
                if (end != -1) {
                    val spriteId = text.substring(i + 1, end)
                    val def = HudSpriteRegistry.getOrNull(spriteId)
                    if (def != null) {
                        emitSprite(parts, spriteId, baseX, baseY, offset)
                        offset += def.columns.size
                    }
                    i = end + 1
                    continue
                }
            }
            val ch = text[i]
            if (ch == ' ') {
                offset++
            } else {
                val spriteId = charToSpriteId(ch)
                if (spriteId != null) {
                    emitSprite(parts, spriteId, baseX, baseY, offset)
                    offset += HudSpriteRegistry.columnCount(spriteId)
                }
            }
            i++
        }
    }

    private fun renderGroup(parts: MutableList<Component>, element: GroupElement, hud: PlayerHud) {
        val (baseX, baseY) = computePosition(element)
        val items = hud.groupItems[element.id] ?: return

        var offset = 0
        for (spriteId in items) {
            val def = HudSpriteRegistry.getOrNull(spriteId) ?: continue
            emitSprite(parts, spriteId, baseX, baseY, offset)
            offset += def.columns.size
        }
    }

    private fun renderAnimated(parts: MutableList<Component>, element: AnimatedSpriteElement, hud: PlayerHud) {
        if (element.frames.isEmpty()) return
        val (x, y) = computePosition(element)
        val frameIndex = ((hud.animationTick / element.intervalTicks) % element.frames.size).toInt()
        emitSprite(parts, element.frames[frameIndex], x, y, 0)
    }

    private fun charToSpriteId(ch: Char): String? = when (ch) {
        in '0'..'9' -> "digit_$ch"
        in 'a'..'z' -> "letter_$ch"
        in 'A'..'Z' -> "letter_$ch"
        ':' -> "glyph_colon"
        '/' -> "glyph_slash"
        '.' -> "glyph_dot"
        '%' -> "glyph_percent"
        '-' -> "glyph_dash"
        '_' -> "glyph_underscore"
        '>' -> "glyph_arrow"
        else -> null
    }
}
