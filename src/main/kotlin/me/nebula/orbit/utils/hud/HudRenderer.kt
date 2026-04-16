package me.nebula.orbit.utils.hud

import me.nebula.orbit.utils.hud.font.HudFontProvider
import me.nebula.orbit.utils.hud.font.HudSpriteRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor

object HudRenderer {

    fun render(hud: PlayerHud): Component {
        val parts = mutableListOf<Component>()
        var cursorX = 0

        for (element in hud.layout.elements) {
            if (!hud.isElementVisible(element.id)) continue
            when (element) {
                is SpriteElement -> renderSprite(parts, element, cursorX).also { cursorX = it }
                is BarElement -> renderBar(parts, element, hud, cursorX).also { cursorX = it }
                is TextElement -> renderText(parts, element, hud, cursorX).also { cursorX = it }
                is GroupElement -> renderGroup(parts, element, hud, cursorX).also { cursorX = it }
                is AnimatedSpriteElement -> renderAnimated(parts, element, hud, cursorX).also { cursorX = it }
            }
        }

        return Component.join(JoinConfiguration.noSeparators(), parts)
    }

    private fun elementGuiY(element: HudElement): Int =
        element.anchor.guiY + element.offsetY

    private fun elementGuiX(element: HudElement): Int =
        element.anchor.guiX + element.offsetX

    private fun fontKeyFor(element: HudElement): Key =
        Key.key("minecraft", HudFontProvider.fontKeyForY(elementGuiY(element)))

    private fun emitCursorShift(parts: MutableList<Component>, from: Int, to: Int, font: Key) {
        val delta = to - from
        if (delta == 0) return
        val shiftChars = HudFontProvider.buildShiftChars(delta)
        if (shiftChars.isNotEmpty()) {
            parts += Component.text(shiftChars)
                .color(NamedTextColor.WHITE)
                .font(font)
        }
    }

    private fun emitSpriteChars(
        parts: MutableList<Component>,
        spriteId: String,
        font: Key,
    ): Int {
        val sprite = HudSpriteRegistry.get(spriteId)
        for (col in sprite.columns) {
            parts += Component.text(col.char.toString())
                .color(NamedTextColor.WHITE)
                .font(font)
        }
        return sprite.columns.size * HudSpriteRegistry.CELL_WIDTH
    }

    private fun renderSprite(parts: MutableList<Component>, element: SpriteElement, cursorX: Int): Int {
        val font = fontKeyFor(element)
        val targetX = elementGuiX(element)
        emitCursorShift(parts, cursorX, targetX, font)
        val width = emitSpriteChars(parts, element.spriteId, font)
        return targetX + width
    }

    private fun renderBar(parts: MutableList<Component>, element: BarElement, hud: PlayerHud, cursorX: Int): Int {
        val font = fontKeyFor(element)
        val targetX = elementGuiX(element)
        emitCursorShift(parts, cursorX, targetX, font)

        val value = (hud.values[element.id] as? Number)?.toInt() ?: 0
        val filled = value.coerceIn(0, element.segments)
        var x = targetX

        x += emitSpriteChars(parts, element.bgSprite, font)

        for (i in 0 until element.segments) {
            val sprite = if (i < filled) element.fillSprite else element.emptySprite
            x += emitSpriteChars(parts, sprite, font)
        }

        return x
    }

    private fun renderText(parts: MutableList<Component>, element: TextElement, hud: PlayerHud, cursorX: Int): Int {
        val font = fontKeyFor(element)
        val targetX = elementGuiX(element)
        emitCursorShift(parts, cursorX, targetX, font)

        val text = hud.values[element.id]?.toString() ?: return targetX
        var x = targetX
        var i = 0

        while (i < text.length) {
            if (text[i] == '{') {
                val end = text.indexOf('}', i + 1)
                if (end != -1) {
                    val spriteId = text.substring(i + 1, end)
                    val def = HudSpriteRegistry.getOrNull(spriteId)
                    if (def != null) {
                        x += emitSpriteChars(parts, spriteId, font)
                    }
                    i = end + 1
                    continue
                }
            }
            val ch = text[i]
            if (ch == ' ') {
                x += HudSpriteRegistry.CELL_WIDTH
                emitCursorShift(parts, x - HudSpriteRegistry.CELL_WIDTH, x, font)
            } else {
                val spriteId = charToSpriteId(ch)
                if (spriteId != null) {
                    x += emitSpriteChars(parts, spriteId, font)
                }
            }
            i++
        }

        return x
    }

    private fun renderGroup(parts: MutableList<Component>, element: GroupElement, hud: PlayerHud, cursorX: Int): Int {
        val font = fontKeyFor(element)
        val targetX = elementGuiX(element)
        emitCursorShift(parts, cursorX, targetX, font)

        val items = hud.groupItems[element.id] ?: return targetX
        var x = targetX

        for (spriteId in items) {
            if (HudSpriteRegistry.getOrNull(spriteId) == null) continue
            x += emitSpriteChars(parts, spriteId, font)
        }

        return x
    }

    private fun renderAnimated(
        parts: MutableList<Component>,
        element: AnimatedSpriteElement,
        hud: PlayerHud,
        cursorX: Int,
    ): Int {
        if (element.frames.isEmpty()) return cursorX
        val font = fontKeyFor(element)
        val targetX = elementGuiX(element)
        emitCursorShift(parts, cursorX, targetX, font)
        val frameIndex = ((hud.animationTick / element.intervalTicks) % element.frames.size).toInt()
        val width = emitSpriteChars(parts, element.frames[frameIndex], font)
        return targetX + width
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
