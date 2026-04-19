package me.nebula.orbit.utils.gui

import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

class PaginatedGuiBuilder @PublishedApi internal constructor(
    @PublishedApi internal val title: String,
    @PublishedApi internal val rows: Int,
) {
    init {
        require(rows in 1..6) { "PaginatedGuiBuilder rows must be 1..6, got $rows" }
    }

    @PublishedApi internal val items = mutableListOf<GuiSlot>()
    @PublishedApi internal val staticSlots = mutableMapOf<Int, GuiSlot>()
    @PublishedApi internal var fillItem: ItemStack? = null
    @PublishedApi internal var borderItem: ItemStack? = null
    @PublishedApi internal var explicitContentRange: IntRange? = null
    @PublishedApi internal var clickSound: SoundEvent? = null
    @PublishedApi internal var key: String = "paginated"
    @PublishedApi internal var onPageChange: ((Player, Int) -> Unit)? = null
    @PublishedApi internal var rememberPerPlayer: Boolean = true
    @PublishedApi internal var prevSlot: Int = rows * 9 - 9
    @PublishedApi internal var nextSlot: Int = rows * 9 - 1

    fun item(item: ItemStack, onClick: (Player) -> Unit = {}) {
        items += GuiSlot(item, { ctx -> onClick(ctx.player) })
    }

    fun item(slot: GuiSlot) {
        items += slot
    }

    fun <T> items(collection: Collection<T>, transform: (T) -> ItemStack, onClick: (Player, T) -> Unit = { _, _ -> }) {
        collection.forEach { element ->
            items += GuiSlot(transform(element), { ctx -> onClick(ctx.player, element) })
        }
    }

    fun staticSlot(index: Int, item: ItemStack, onClick: (Player) -> Unit = {}) {
        staticSlots[index] = GuiSlot(item, { ctx -> onClick(ctx.player) })
    }

    fun staticSlot(index: Int, slot: GuiSlot) {
        staticSlots[index] = slot
    }

    fun fill(material: Material) {
        fillItem = itemStack(material) { name(" "); hideTooltip() }
    }

    fun fill(item: ItemStack) {
        fillItem = item
    }

    fun fillDefault() {
        fillItem = DEFAULT_FILLER
    }

    fun border(material: Material) {
        borderItem = itemStack(material) { name(" "); hideTooltip() }
    }

    fun border(item: ItemStack) {
        borderItem = item
    }

    fun borderDefault() {
        borderItem = DEFAULT_BORDER
    }

    fun contentSlots(range: IntRange) {
        explicitContentRange = range
    }

    fun clickSound(sound: SoundEvent) {
        clickSound = sound
    }

    fun key(key: String) {
        this.key = key
    }

    fun onPageChange(handler: (Player, Int) -> Unit) {
        onPageChange = handler
    }

    fun rememberPage(remember: Boolean) {
        rememberPerPlayer = remember
    }

    fun navigationSlots(prev: Int, next: Int) {
        prevSlot = prev
        nextSlot = next
    }

    fun backButton(index: Int, onClick: (Player) -> Unit) {
        staticSlots[index] = GuiSlot(
            itemStack(Material.ARROW) { name("<gray>Back"); clean() },
            { ctx -> onClick(ctx.player) },
            SoundEvent.UI_BUTTON_CLICK,
        )
    }

    @PublishedApi internal fun build(): PaginatedGui {
        val size = rows * 9
        val contentList = resolveContentSlots(size)
        return PaginatedGui(
            title = title,
            rows = rows,
            items = items.toList(),
            staticSlots = staticSlots.toMap(),
            fillItem = fillItem,
            borderItem = borderItem,
            contentSlots = contentList,
            clickSound = clickSound,
            key = key,
            onPageChange = onPageChange,
            rememberPerPlayer = rememberPerPlayer,
            prevSlot = prevSlot,
            nextSlot = nextSlot,
        )
    }

    private fun resolveContentSlots(size: Int): List<Int> {
        val explicit = explicitContentRange
        if (explicit != null) {
            explicit.forEach { require(it in 0 until size) { "Content slot $it out of bounds for size $size" } }
            val reserved = staticSlots.keys + setOfNotNull(prevSlot, nextSlot)
            return explicit.filter { it !in reserved }
        }
        val hasBorder = borderItem != null
        val reserved = staticSlots.keys + setOfNotNull(prevSlot, nextSlot)
        val cols = 9
        val totalRows = size / cols
        val candidates = (0 until size).filter { i ->
            val row = i / cols
            val col = i % cols
            val isBorder = row == 0 || row == totalRows - 1 || col == 0 || col == cols - 1
            !(hasBorder && isBorder) && i !in reserved
        }
        return candidates
    }
}

inline fun paginatedGui(title: String, rows: Int = 6, block: PaginatedGuiBuilder.() -> Unit): PaginatedGui =
    PaginatedGuiBuilder(title, rows).apply(block).build()
