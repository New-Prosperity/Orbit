package me.nebula.orbit.utils.gui

import me.nebula.orbit.utils.itembuilder.itemStack
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

object EnumPicker {

    fun <T : Enum<T>> open(
        player: Player,
        title: String,
        values: Array<T>,
        current: T? = null,
        label: (T) -> String = { it.name },
        icon: (T) -> Material = { Material.PAPER },
        onPick: (T) -> Unit,
    ) {
        openGeneric(
            player = player,
            title = title,
            values = values.toList(),
            current = current,
            label = label,
            icon = icon,
            onPick = onPick,
        )
    }

    fun <T> openGeneric(
        player: Player,
        title: String,
        values: List<T>,
        current: T? = null,
        label: (T) -> String = { it.toString() },
        icon: (T) -> Material = { Material.PAPER },
        onPick: (T) -> Unit,
    ) {
        require(values.isNotEmpty()) { "Enum picker requires at least 1 value" }

        val paginated = paginatedGui(title, rows = 6) {
            borderDefault()
            rememberPage(false)
            key("enum-picker")
            items<T>(values, transform = { v ->
                val isCurrent = v == current
                itemStack(icon(v)) {
                    name(if (isCurrent) "<green>${label(v)}" else "<white>${label(v)}")
                    if (isCurrent) {
                        lore("<gray>Currently selected")
                        glowing()
                    } else {
                        lore("<yellow>Click to select")
                    }
                    clean()
                }
            }) { p, value ->
                onPick(value)
                p.closeInventory()
            }
            staticSlot(49, itemStack(Material.BARRIER) { name("<red>Close"); clean() }) { p -> p.closeInventory() }
        }
        paginated.openForPlayer(player)
    }

    fun <T> renderItem(value: T, label: (T) -> String, icon: (T) -> Material, isCurrent: Boolean): ItemStack =
        itemStack(icon(value)) {
            name(if (isCurrent) "<green>${label(value)}" else "<white>${label(value)}")
            if (isCurrent) {
                lore("<gray>Currently selected")
                glowing()
            } else {
                lore("<yellow>Click to select")
            }
            clean()
        }
}
