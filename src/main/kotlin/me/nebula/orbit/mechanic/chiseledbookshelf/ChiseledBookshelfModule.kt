package me.nebula.orbit.mechanic.chiseledbookshelf

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap

private data class BookshelfKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val BOOK_MATERIALS = setOf(
    Material.BOOK, Material.WRITABLE_BOOK, Material.WRITTEN_BOOK,
    Material.ENCHANTED_BOOK, Material.KNOWLEDGE_BOOK,
)

class ChiseledBookshelfModule : OrbitModule("chiseled-bookshelf") {

    private val shelfBooks = ConcurrentHashMap<BookshelfKey, MutableList<Material>>()

    override fun onEnable() {
        super.onEnable()
        shelfBooks.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:chiseled_bookshelf") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = BookshelfKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val held = event.player.getItemInMainHand()
            val books = shelfBooks.getOrPut(key) { mutableListOf() }

            if (held.material() in BOOK_MATERIALS && books.size < 6) {
                books.add(held.material())
                val consumed = held.consume(1)
                event.player.setItemInMainHand(consumed)
                updateSlotProperties(instance, pos, event.block, books)
            } else if (held.isAir && books.isNotEmpty()) {
                val removed = books.removeLast()
                event.player.inventory.addItemStack(net.minestom.server.item.ItemStack.of(removed))
                updateSlotProperties(instance, pos, event.block, books)
            }
        }
    }

    private fun updateSlotProperties(
        instance: net.minestom.server.instance.Instance,
        pos: net.minestom.server.coordinate.Point,
        block: net.minestom.server.instance.block.Block,
        books: List<Material>,
    ) {
        var updated = block
        for (i in 0..5) {
            val occupied = i < books.size
            updated = updated.withProperty("slot_${i}_occupied", occupied.toString())
        }
        instance.setBlock(pos, updated)
    }

    override fun onDisable() {
        shelfBooks.clear()
        super.onDisable()
    }
}
