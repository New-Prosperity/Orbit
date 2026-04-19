package me.nebula.orbit.utils.gui

import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class PaginatedGui internal constructor(
    private val title: String,
    private val rows: Int,
    private val items: List<GuiSlot>,
    private val staticSlots: Map<Int, GuiSlot>,
    private val fillItem: ItemStack?,
    private val borderItem: ItemStack?,
    private val contentSlots: List<Int>,
    private val clickSound: SoundEvent?,
    private val key: String,
    private val onPageChange: ((Player, Int) -> Unit)?,
    private val rememberPerPlayer: Boolean,
    private val prevSlot: Int,
    private val nextSlot: Int,
) {
    private val perPlayerPage = ConcurrentHashMap<UUID, Int>()
    val pageSize: Int = contentSlots.size.coerceAtLeast(1)
    val totalPages: Int get() = if (items.isEmpty()) 1 else ((items.size + pageSize - 1) / pageSize)

    fun openForPlayer(player: Player) {
        val page = if (rememberPerPlayer) perPlayerPage[player.uuid] ?: 0 else 0
        open(player, page)
    }

    fun jumpToPage(player: Player, page: Int) {
        open(player, page)
    }

    fun currentPage(player: Player): Int =
        perPlayerPage[player.uuid] ?: 0

    fun clearPage(player: Player) {
        perPlayerPage.remove(player.uuid)
    }

    fun open(player: Player, page: Int = 0) {
        val safePage = page.coerceIn(0, totalPages - 1)
        if (rememberPerPlayer) perPlayerPage[player.uuid] = safePage

        val builder = GuiBuilder("$title <dark_gray>(${safePage + 1}/$totalPages)", rows)
        fillItem?.let { builder.fill(it) }
        borderItem?.let { builder.border(it) }
        clickSound?.let { builder.clickSound(it) }

        val offset = safePage * pageSize
        contentSlots.forEachIndexed { i, slot ->
            val itemIndex = offset + i
            if (itemIndex < items.size) {
                builder.slot(slot, items[itemIndex])
            }
        }

        staticSlots.forEach { (slot, gs) -> builder.slot(slot, gs) }

        if (safePage > 0) {
            builder.slot(prevSlot, GuiSlot(
                itemStack(Material.ARROW) { name("<yellow>Previous Page"); clean() },
                { ctx -> open(ctx.player, safePage - 1) },
                SoundEvent.UI_BUTTON_CLICK,
            ))
        }
        if (safePage < totalPages - 1) {
            builder.slot(nextSlot, GuiSlot(
                itemStack(Material.ARROW) { name("<yellow>Next Page"); clean() },
                { ctx -> open(ctx.player, safePage + 1) },
                SoundEvent.UI_BUTTON_CLICK,
            ))
        }

        onPageChange?.invoke(player, safePage)
        builder.build().open(player)
    }

    internal fun pageKey(): String = key

    internal fun onDisconnect(uuid: UUID) {
        perPlayerPage.remove(uuid)
    }

    companion object {
        fun pageCountOf(itemCount: Int, pageSize: Int): Int {
            val ps = pageSize.coerceAtLeast(1)
            return if (itemCount <= 0) 1 else (itemCount + ps - 1) / ps
        }
    }
}
