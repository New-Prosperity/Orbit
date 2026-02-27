package me.nebula.orbit.utils.screen

import me.nebula.orbit.utils.screen.canvas.MapCanvas
import me.nebula.orbit.utils.screen.font.BitmapFont
import me.nebula.orbit.utils.screen.texture.Texture
import me.nebula.orbit.utils.screen.widget.Button
import me.nebula.orbit.utils.screen.widget.ImageWidget
import me.nebula.orbit.utils.screen.widget.Label
import me.nebula.orbit.utils.screen.widget.Panel
import me.nebula.orbit.utils.screen.widget.ProgressBar
import me.nebula.orbit.utils.screen.widget.Widget
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class CursorBuilder @PublishedApi internal constructor() {
    var item: ItemStack = ItemStack.of(Material.ARROW)
    var scale: Float = 0.06f
    var interpolationTicks: Int = 1

    fun item(item: ItemStack) { this.item = item }
    fun scale(scale: Float) { this.scale = scale }
    fun interpolationTicks(ticks: Int) { this.interpolationTicks = ticks }
}

class ButtonBuilder @PublishedApi internal constructor(
    val id: String,
    val pixelX: Int,
    val pixelY: Int,
    val pixelWidth: Int,
    val pixelHeight: Int,
) {
    var onClick: (() -> Unit)? = null
    var onHover: ((Boolean) -> Unit)? = null

    fun onClick(action: () -> Unit) { this.onClick = action }
    fun onHover(action: (Boolean) -> Unit) { this.onHover = action }
}

class WidgetButtonBuilder @PublishedApi internal constructor(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val text: String,
    val font: BitmapFont,
) {
    var bgColor: Int = 0xFF333333.toInt()
    var hoverColor: Int = 0xFF555555.toInt()
    var textColor: Int = 0xFFFFFFFF.toInt()
    var cornerRadius: Int = 0
    var onClickAction: (() -> Unit)? = null

    fun bgColor(color: Int) { bgColor = color }
    fun hoverColor(color: Int) { hoverColor = color }
    fun textColor(color: Int) { textColor = color }
    fun cornerRadius(radius: Int) { cornerRadius = radius }
    fun onClick(action: () -> Unit) { onClickAction = action }
}

class ProgressBarBuilder @PublishedApi internal constructor(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    var progress: Float = 0f
    var fgColor: Int = 0xFF4CAF50.toInt()
    var bgColor: Int = 0xFF333333.toInt()
    var cornerRadius: Int = 0

    fun progress(value: Float) { progress = value }
    fun fgColor(color: Int) { fgColor = color }
    fun bgColor(color: Int) { bgColor = color }
    fun cornerRadius(radius: Int) { cornerRadius = radius }
}

class PanelBuilder @PublishedApi internal constructor(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
    val color: Int,
) {
    var cornerRadius: Int = 0
    @PublishedApi internal val children = mutableListOf<Widget>()

    fun cornerRadius(radius: Int) { cornerRadius = radius }

    fun label(x: Int, y: Int, text: String, font: BitmapFont, color: Int) {
        children += Label(x, y, text, font, color)
    }

    inline fun button(
        x: Int, y: Int, w: Int, h: Int,
        text: String, font: BitmapFont,
        block: WidgetButtonBuilder.() -> Unit,
    ) {
        val b = WidgetButtonBuilder(x, y, w, h, text, font).apply(block)
        children += Button(x, y, w, h, text, font, b.bgColor, b.hoverColor, b.cornerRadius, b.textColor, b.onClickAction)
    }

    inline fun progressBar(x: Int, y: Int, w: Int, h: Int, block: ProgressBarBuilder.() -> Unit) {
        val b = ProgressBarBuilder(x, y, w, h).apply(block)
        children += ProgressBar(x, y, w, h, b.progress, b.fgColor, b.bgColor, b.cornerRadius)
    }

    fun image(x: Int, y: Int, texture: Texture) {
        children += ImageWidget(x, y, texture)
    }
}

class ScreenBuilder @PublishedApi internal constructor() {
    @PublishedApi internal val cursorBuilder = CursorBuilder()
    @PublishedApi internal val buttons = mutableListOf<ButtonBuilder>()
    @PublishedApi internal var sensitivity = 1.0
    @PublishedApi internal var closeOnSneak = false
    @PublishedApi internal var onClose: (() -> Unit)? = null
    @PublishedApi internal var backgroundColor: Int = 0xFF000000.toInt()
    @PublishedApi internal var backgroundImage: IntArray? = null
    @PublishedApi internal var backgroundImageWidth = 0
    @PublishedApi internal var backgroundImageHeight = 0
    @PublishedApi internal var onDraw: ((MapCanvas) -> Unit)? = null
    @PublishedApi internal val widgets = mutableListOf<Widget>()

    inline fun cursor(block: CursorBuilder.() -> Unit) { cursorBuilder.apply(block) }

    inline fun button(
        id: String,
        x: Int,
        y: Int,
        w: Int,
        h: Int,
        block: ButtonBuilder.() -> Unit,
    ) {
        buttons += ButtonBuilder(id, x, y, w, h).apply(block)
    }

    fun background(color: Int) { backgroundColor = color }
    fun background(image: IntArray, width: Int, height: Int) {
        backgroundImage = image
        backgroundImageWidth = width
        backgroundImageHeight = height
    }

    fun sensitivity(value: Double) { sensitivity = value }
    fun escToClose() { closeOnSneak = true }
    fun onClose(action: () -> Unit) { onClose = action }
    fun onDraw(action: (MapCanvas) -> Unit) { onDraw = action }

    inline fun panel(
        x: Int, y: Int, w: Int, h: Int,
        color: Int,
        block: PanelBuilder.() -> Unit = {},
    ) {
        val b = PanelBuilder(x, y, w, h, color).apply(block)
        val panel = Panel(x, y, w, h, color, b.cornerRadius)
        for (child in b.children) panel.addChild(child)
        widgets += panel
    }

    fun label(x: Int, y: Int, text: String, font: BitmapFont, color: Int) {
        widgets += Label(x, y, text, font, color)
    }

    inline fun button(
        x: Int, y: Int, w: Int, h: Int,
        text: String, font: BitmapFont,
        block: WidgetButtonBuilder.() -> Unit,
    ) {
        val b = WidgetButtonBuilder(x, y, w, h, text, font).apply(block)
        widgets += Button(x, y, w, h, text, font, b.bgColor, b.hoverColor, b.cornerRadius, b.textColor, b.onClickAction)
    }

    inline fun progressBar(x: Int, y: Int, w: Int, h: Int, block: ProgressBarBuilder.() -> Unit) {
        val b = ProgressBarBuilder(x, y, w, h).apply(block)
        widgets += ProgressBar(x, y, w, h, b.progress, b.fgColor, b.bgColor, b.cornerRadius)
    }

    fun image(x: Int, y: Int, texture: Texture) {
        widgets += ImageWidget(x, y, texture)
    }
}
