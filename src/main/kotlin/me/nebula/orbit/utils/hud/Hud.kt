package me.nebula.orbit.utils.hud

enum class Direction {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
}

enum class HudAnchor(val nx: Float, val ny: Float) {
    TOP_LEFT(0f, 0f),
    TOP_CENTER(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    CENTER_LEFT(0f, 0.5f),
    CENTER(0.5f, 0.5f),
    CENTER_RIGHT(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_CENTER(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f),
}

sealed interface HudElement {
    val id: String
    val anchor: HudAnchor
    val offsetX: Float
    val offsetY: Float
}

data class SpriteElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Float,
    override val offsetY: Float,
    val spriteId: String,
) : HudElement

data class BarElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Float,
    override val offsetY: Float,
    val bgSprite: String,
    val fillSprite: String,
    val emptySprite: String,
    val segments: Int,
    val direction: Direction = Direction.LEFT_TO_RIGHT,
) : HudElement

data class TextElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Float,
    override val offsetY: Float,
) : HudElement

data class GroupElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Float,
    override val offsetY: Float,
    val direction: Direction,
    val spacing: Float,
    val children: List<String>,
) : HudElement

data class AnimatedSpriteElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Float,
    override val offsetY: Float,
    val frames: List<String>,
    val intervalTicks: Int,
) : HudElement

data class HudLayout(
    val id: String,
    val elements: List<HudElement>,
    val layer: Int = 0,
)

class BarElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0f
    private var offsetY = 0f
    private var bgSprite = ""
    private var fillSprite = ""
    private var emptySprite = ""
    private var segments = 10
    private var direction = Direction.LEFT_TO_RIGHT

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Float, y: Float) { offsetX = x; offsetY = y }
    fun sprites(bg: String, fill: String, empty: String) { bgSprite = bg; fillSprite = fill; emptySprite = empty }
    fun segments(count: Int) { segments = count }
    fun direction(dir: Direction) { direction = dir }

    @PublishedApi internal fun build(): BarElement =
        BarElement(id, anchor, offsetX, offsetY, bgSprite, fillSprite, emptySprite, segments, direction)
}

class SpriteElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0f
    private var offsetY = 0f
    private var spriteId = ""

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Float, y: Float) { offsetX = x; offsetY = y }
    fun sprite(id: String) { spriteId = id }

    @PublishedApi internal fun build(): SpriteElement =
        SpriteElement(id, anchor, offsetX, offsetY, spriteId)
}

class TextElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0f
    private var offsetY = 0f

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Float, y: Float) { offsetX = x; offsetY = y }

    @PublishedApi internal fun build(): TextElement =
        TextElement(id, anchor, offsetX, offsetY)
}

class GroupElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0f
    private var offsetY = 0f
    private var direction = Direction.LEFT_TO_RIGHT
    private var spacing = 0.01f

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Float, y: Float) { offsetX = x; offsetY = y }
    fun horizontal(spacing: Float = 0.01f) { direction = Direction.LEFT_TO_RIGHT; this.spacing = spacing }
    fun vertical(spacing: Float = 0.01f) { direction = Direction.TOP_TO_BOTTOM; this.spacing = spacing }

    @PublishedApi internal fun build(): GroupElement =
        GroupElement(id, anchor, offsetX, offsetY, direction, spacing, emptyList())
}

class AnimatedSpriteElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0f
    private var offsetY = 0f
    private val frameList = mutableListOf<String>()
    private var intervalTicks = 5

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Float, y: Float) { offsetX = x; offsetY = y }
    fun frames(vararg ids: String) { frameList += ids }
    fun interval(ticks: Int) { intervalTicks = ticks }

    @PublishedApi internal fun build(): AnimatedSpriteElement =
        AnimatedSpriteElement(id, anchor, offsetX, offsetY, frameList.toList(), intervalTicks)
}

class HudLayoutBuilder @PublishedApi internal constructor(private val id: String) {
    @PublishedApi internal val elements = mutableListOf<HudElement>()
    private var layer = 0

    inline fun bar(id: String, block: BarElementBuilder.() -> Unit) {
        elements += BarElementBuilder(id).apply(block).build()
    }

    inline fun sprite(id: String, block: SpriteElementBuilder.() -> Unit) {
        elements += SpriteElementBuilder(id).apply(block).build()
    }

    inline fun text(id: String, block: TextElementBuilder.() -> Unit) {
        elements += TextElementBuilder(id).apply(block).build()
    }

    inline fun group(id: String, block: GroupElementBuilder.() -> Unit) {
        elements += GroupElementBuilder(id).apply(block).build()
    }

    inline fun animated(id: String, block: AnimatedSpriteElementBuilder.() -> Unit) {
        elements += AnimatedSpriteElementBuilder(id).apply(block).build()
    }

    fun layer(layer: Int) { this.layer = layer }

    @PublishedApi internal fun build(): HudLayout =
        HudLayout(id, elements.toList(), layer)
}

inline fun hudLayout(id: String, block: HudLayoutBuilder.() -> Unit): HudLayout =
    HudLayoutBuilder(id).apply(block).build()
