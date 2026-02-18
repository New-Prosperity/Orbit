package me.nebula.orbit.utils.pagination

import me.nebula.orbit.translation.translateDefault
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.entity.Player

class PaginatedView<T>(
    val items: List<T>,
    val pageSize: Int = 10,
    val renderer: (T, Int) -> Component,
    val header: (Int, Int) -> Component = { page, total ->
        translateDefault("orbit.util.pagination.header", "page" to "$page", "total" to "$total").color(NamedTextColor.GOLD)
    },
    val footer: (Int, Int) -> Component = { _, _ ->
        translateDefault("orbit.util.pagination.navigate").color(NamedTextColor.GRAY)
    },
) {
    val totalPages: Int get() = ((items.size - 1) / pageSize + 1).coerceAtLeast(1)

    fun getPage(page: Int): List<T> {
        val validPage = page.coerceIn(1, totalPages)
        val start = (validPage - 1) * pageSize
        val end = (start + pageSize).coerceAtMost(items.size)
        return if (start < items.size) items.subList(start, end) else emptyList()
    }

    fun render(page: Int): List<Component> {
        val validPage = page.coerceIn(1, totalPages)
        val pageItems = getPage(validPage)
        return buildList {
            add(header(validPage, totalPages))
            add(Component.empty())
            pageItems.forEachIndexed { index, item ->
                add(renderer(item, (validPage - 1) * pageSize + index))
            }
            if (totalPages > 1) {
                add(Component.empty())
                add(footer(validPage, totalPages))
            }
        }
    }

    fun send(player: Player, page: Int) {
        render(page).forEach { player.sendMessage(it) }
    }
}

class PaginatedViewBuilder<T> {
    var pageSize: Int = 10
    private var items: List<T> = emptyList()
    private var renderer: (T, Int) -> Component = { item, _ -> Component.text(item.toString()) }
    private var header: (Int, Int) -> Component = { page, total ->
        translateDefault("orbit.util.pagination.header", "page" to "$page", "total" to "$total").color(NamedTextColor.GOLD)
    }
    private var footer: (Int, Int) -> Component = { _, _ ->
        translateDefault("orbit.util.pagination.navigate").color(NamedTextColor.GRAY)
    }

    fun items(list: List<T>) {
        items = list
    }

    fun render(block: (T, Int) -> Component) {
        renderer = block
    }

    fun header(block: (Int, Int) -> Component) {
        header = block
    }

    fun footer(block: (Int, Int) -> Component) {
        footer = block
    }

    fun build(): PaginatedView<T> = PaginatedView(items, pageSize, renderer, header, footer)
}

fun <T> paginatedView(block: PaginatedViewBuilder<T>.() -> Unit): PaginatedView<T> =
    PaginatedViewBuilder<T>().apply(block).build()
