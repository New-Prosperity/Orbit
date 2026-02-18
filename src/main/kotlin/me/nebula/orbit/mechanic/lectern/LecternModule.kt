package me.nebula.orbit.mechanic.lectern

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.component.DataComponents
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.util.concurrent.ConcurrentHashMap

private data class LecternKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class LecternModule : OrbitModule("lectern") {

    private val books = ConcurrentHashMap<LecternKey, ItemStack>()

    override fun onEnable() {
        super.onEnable()
        books.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:lectern") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = LecternKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            val existingBook = books[key]
            if (existingBook != null) {
                val pages = existingBook.get(DataComponents.WRITABLE_BOOK_CONTENT)
                event.player.sendMessage(event.player.translate("orbit.mechanic.lectern.book_header"))
                if (pages == null) {
                    event.player.sendMessage(event.player.translate("orbit.mechanic.lectern.empty"))
                }
                return@addListener
            }

            val held = event.player.getItemInMainHand()
            if (held.material() != Material.WRITTEN_BOOK && held.material() != Material.WRITABLE_BOOK) return@addListener

            books[key] = held
            instance.setBlock(pos, event.block.withProperty("has_book", "true"))

            val slot = event.player.heldSlot.toInt()
            if (held.amount() > 1) {
                event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                event.player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }
    }

    override fun onDisable() {
        books.clear()
        super.onDisable()
    }
}
