package me.nebula.orbit.utils.hud

import me.nebula.orbit.utils.hud.font.HudSpriteRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.TextColor

object HudRenderer {

    val HUD_FONT: Key = Key.key("minecraft", "hud")
    private const val CHAR_STEP = 3f / 255f

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

    private fun encodeColor(x: Float, y: Float): TextColor =
        TextColor.color(
            (x * 255).toInt().coerceIn(0, 255),
            (y * 255).toInt().coerceIn(0, 255),
            254,
        )

    private fun spriteComponent(spriteId: String, x: Float, y: Float): Component =
        Component.text(HudSpriteRegistry.charFor(spriteId).toString())
            .color(encodeColor(x, y))
            .font(HUD_FONT)

    private fun renderSprite(parts: MutableList<Component>, element: SpriteElement) {
        val (x, y) = computePosition(element)
        parts += spriteComponent(element.spriteId, x, y)
    }

    private fun renderBar(parts: MutableList<Component>, element: BarElement, hud: PlayerHud) {
        val (baseX, baseY) = computePosition(element)
        val value = (hud.values[element.id] as? Number)?.toInt() ?: 0
        val filled = value.coerceIn(0, element.segments)

        val (dx, dy) = directionStep(element.direction, CHAR_STEP)

        parts += spriteComponent(element.bgSprite, baseX, baseY)
        for (i in 0 until element.segments) {
            val x = baseX + dx * (i + 1)
            val y = baseY + dy * (i + 1)
            val sprite = if (i < filled) element.fillSprite else element.emptySprite
            parts += spriteComponent(sprite, x, y)
        }
    }

    private fun renderText(parts: MutableList<Component>, element: TextElement, hud: PlayerHud) {
        val (baseX, baseY) = computePosition(element)
        val text = hud.values[element.id]?.toString() ?: return

        var x = baseX
        var i = 0
        while (i < text.length) {
            if (text[i] == '{') {
                val end = text.indexOf('}', i + 1)
                if (end != -1) {
                    val spriteId = text.substring(i + 1, end)
                    if (HudSpriteRegistry.getOrNull(spriteId) != null) {
                        parts += spriteComponent(spriteId, x, baseY)
                        x += CHAR_STEP
                    }
                    i = end + 1
                    continue
                }
            }
            val ch = text[i]
            if (ch == ' ') {
                x += CHAR_STEP
            } else {
                val spriteId = charToSpriteId(ch)
                if (spriteId != null) {
                    parts += spriteComponent(spriteId, x, baseY)
                    x += CHAR_STEP
                }
            }
            i++
        }
    }

    private fun renderGroup(parts: MutableList<Component>, element: GroupElement, hud: PlayerHud) {
        val (baseX, baseY) = computePosition(element)
        val items = hud.groupItems[element.id] ?: return

        val (dx, dy) = directionStep(element.direction, element.spacing)
        items.forEachIndexed { i, spriteId ->
            parts += spriteComponent(spriteId, baseX + dx * i, baseY + dy * i)
        }
    }

    private fun renderAnimated(parts: MutableList<Component>, element: AnimatedSpriteElement, hud: PlayerHud) {
        if (element.frames.isEmpty()) return
        val (x, y) = computePosition(element)
        val frameIndex = ((hud.animationTick / element.intervalTicks) % element.frames.size).toInt()
        parts += spriteComponent(element.frames[frameIndex], x, y)
    }

    private fun directionStep(direction: Direction, step: Float): Pair<Float, Float> = when (direction) {
        Direction.LEFT_TO_RIGHT -> step to 0f
        Direction.RIGHT_TO_LEFT -> -step to 0f
        Direction.TOP_TO_BOTTOM -> 0f to step
        Direction.BOTTOM_TO_TOP -> 0f to -step
    }

    private fun charToSpriteId(ch: Char): String? = when (ch) {
        in '0'..'9' -> "digit_$ch"
        ':' -> "glyph_colon"
        '/' -> "glyph_slash"
        '.' -> "glyph_dot"
        '%' -> "glyph_percent"
        else -> null
    }
}
