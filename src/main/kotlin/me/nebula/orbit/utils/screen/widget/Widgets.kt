package me.nebula.orbit.utils.screen.widget

import me.nebula.orbit.utils.screen.canvas.MapCanvas
import me.nebula.orbit.utils.screen.canvas.roundedRect
import me.nebula.orbit.utils.screen.font.BitmapFont
import me.nebula.orbit.utils.screen.font.drawText
import me.nebula.orbit.utils.screen.font.textWidth
import me.nebula.orbit.utils.screen.texture.Texture
import me.nebula.orbit.utils.screen.texture.drawTexture

class Panel(
    x: Int, y: Int, w: Int, h: Int,
    val color: Int,
    val cornerRadius: Int = 0,
) : Widget(x, y, w, h) {

    override fun render(canvas: MapCanvas, absX: Int, absY: Int) {
        if (cornerRadius > 0) {
            canvas.roundedRect(absX, absY, width, height, cornerRadius, color)
        } else {
            canvas.fill(absX, absY, width, height, color)
        }
    }
}

class Label(
    x: Int, y: Int,
    var text: String,
    val font: BitmapFont,
    var color: Int,
) : Widget(x, y, textWidth(font, text), font.charHeight) {

    override fun render(canvas: MapCanvas, absX: Int, absY: Int) {
        canvas.drawText(font, absX, absY, text, color)
    }
}

class Button(
    x: Int, y: Int, w: Int, h: Int,
    var text: String,
    val font: BitmapFont,
    var bgColor: Int,
    var hoverColor: Int,
    val cornerRadius: Int = 0,
    var textColor: Int = 0xFFFFFFFF.toInt(),
    private val onClickAction: (() -> Unit)? = null,
) : Widget(x, y, w, h) {

    @Volatile
    var hovered: Boolean = false

    override fun render(canvas: MapCanvas, absX: Int, absY: Int) {
        val bg = if (hovered) hoverColor else bgColor
        if (cornerRadius > 0) {
            canvas.roundedRect(absX, absY, width, height, cornerRadius, bg)
        } else {
            canvas.fill(absX, absY, width, height, bg)
        }
        val tw = textWidth(font, text)
        val tx = absX + (width - tw) / 2
        val ty = absY + (height - font.charHeight) / 2
        canvas.drawText(font, tx, ty, text, textColor)
    }

    override fun onHover(hovering: Boolean) {
        hovered = hovering
    }

    override fun onClick() {
        onClickAction?.invoke()
    }
}

class ProgressBar(
    x: Int, y: Int, w: Int, h: Int,
    var progress: Float,
    var fgColor: Int,
    var bgColor: Int,
    val cornerRadius: Int = 0,
) : Widget(x, y, w, h) {

    override fun render(canvas: MapCanvas, absX: Int, absY: Int) {
        if (cornerRadius > 0) {
            canvas.roundedRect(absX, absY, width, height, cornerRadius, bgColor)
        } else {
            canvas.fill(absX, absY, width, height, bgColor)
        }
        val fillW = (width * progress.coerceIn(0f, 1f)).toInt()
        if (fillW > 0) {
            if (cornerRadius > 0) {
                canvas.roundedRect(absX, absY, fillW, height, cornerRadius, fgColor)
            } else {
                canvas.fill(absX, absY, fillW, height, fgColor)
            }
        }
    }
}

class ImageWidget(
    x: Int, y: Int,
    val texture: Texture,
) : Widget(x, y, texture.width, texture.height) {

    override fun render(canvas: MapCanvas, absX: Int, absY: Int) {
        canvas.drawTexture(absX, absY, texture)
    }
}
