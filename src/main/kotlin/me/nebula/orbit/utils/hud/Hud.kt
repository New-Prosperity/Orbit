package me.nebula.orbit.utils.hud

enum class Direction {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
}

enum class HudAnchor(val guiX: Int, val guiY: Int) {
    TOP_LEFT(0, 0),
    TOP_CENTER(228, 0),
    TOP_RIGHT(440, 0),
    CENTER_LEFT(0, 128),
    CENTER(228, 128),
    CENTER_RIGHT(440, 128),
    BOTTOM_LEFT(0, 230),
    BOTTOM_CENTER(228, 230),
    BOTTOM_RIGHT(440, 230),
}

sealed interface HudElement {
    val id: String
    val anchor: HudAnchor
    val offsetX: Int
    val offsetY: Int
}

data class SpriteElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Int,
    override val offsetY: Int,
    val spriteId: String,
) : HudElement

data class BarElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Int,
    override val offsetY: Int,
    val bgSprite: String,
    val fillSprite: String,
    val emptySprite: String,
    val segments: Int,
    val direction: Direction = Direction.LEFT_TO_RIGHT,
) : HudElement

data class TextElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Int,
    override val offsetY: Int,
) : HudElement

data class GroupElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Int,
    override val offsetY: Int,
    val direction: Direction,
    val spacing: Int,
    val children: List<String>,
) : HudElement

data class AnimatedSpriteElement(
    override val id: String,
    override val anchor: HudAnchor,
    override val offsetX: Int,
    override val offsetY: Int,
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
    private var offsetX = 0
    private var offsetY = 0
    private var bgSprite = ""
    private var fillSprite = ""
    private var emptySprite = ""
    private var segments = 10
    private var direction = Direction.LEFT_TO_RIGHT

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Int, y: Int) { offsetX = x; offsetY = y }
    fun sprites(bg: String, fill: String, empty: String) { bgSprite = bg; fillSprite = fill; emptySprite = empty }
    fun segments(count: Int) { segments = count }
    fun direction(dir: Direction) { direction = dir }

    @PublishedApi internal fun build(): BarElement =
        BarElement(id, anchor, offsetX, offsetY, bgSprite, fillSprite, emptySprite, segments, direction)
}

class SpriteElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0
    private var offsetY = 0
    private var spriteId = ""

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Int, y: Int) { offsetX = x; offsetY = y }
    fun sprite(id: String) { spriteId = id }

    @PublishedApi internal fun build(): SpriteElement =
        SpriteElement(id, anchor, offsetX, offsetY, spriteId)
}

class TextElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0
    private var offsetY = 0

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Int, y: Int) { offsetX = x; offsetY = y }

    @PublishedApi internal fun build(): TextElement =
        TextElement(id, anchor, offsetX, offsetY)
}

class GroupElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0
    private var offsetY = 0
    private var direction = Direction.LEFT_TO_RIGHT
    private var spacing = 2

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Int, y: Int) { offsetX = x; offsetY = y }
    fun horizontal(spacing: Int = 2) { direction = Direction.LEFT_TO_RIGHT; this.spacing = spacing }
    fun vertical(spacing: Int = 2) { direction = Direction.TOP_TO_BOTTOM; this.spacing = spacing }

    @PublishedApi internal fun build(): GroupElement =
        GroupElement(id, anchor, offsetX, offsetY, direction, spacing, emptyList())
}

class AnimatedSpriteElementBuilder @PublishedApi internal constructor(private val id: String) {
    private var anchor = HudAnchor.TOP_LEFT
    private var offsetX = 0
    private var offsetY = 0
    private val frameList = mutableListOf<String>()
    private var intervalTicks = 5

    fun anchor(anchor: HudAnchor) { this.anchor = anchor }
    fun offset(x: Int, y: Int) { offsetX = x; offsetY = y }
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
