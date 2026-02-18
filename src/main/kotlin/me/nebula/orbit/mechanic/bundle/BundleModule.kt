package me.nebula.orbit.mechanic.bundle

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.entity.Player
import net.minestom.server.event.inventory.InventoryCloseEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val BUNDLE_ID_TAG = Tag.String("mechanic:bundle:id")

private data class BundleSession(
    val hand: net.minestom.server.entity.PlayerHand,
    val inventory: Inventory,
    val bundleId: String,
)

class BundleModule : OrbitModule("bundle") {

    private val openBundles = ConcurrentHashMap<UUID, BundleSession>()
    private val bundleContents = ConcurrentHashMap<String, List<ItemStack>>()

    override fun onEnable() {
        super.onEnable()

        onPlayerDisconnect { openBundles.remove(it.uuid) }

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.BUNDLE) return@addListener

            val player = event.player
            val hand = event.hand
            val bundle = event.itemStack

            val bundleId = bundle.getTag(BUNDLE_ID_TAG)
                ?: UUID.randomUUID().toString().also { id ->
                    val tagged = bundle.withTag(BUNDLE_ID_TAG, id)
                    player.setItemInHand(hand, tagged)
                }

            val inventory = Inventory(InventoryType.CHEST_1_ROW, player.translate("orbit.mechanic.bundle.title"))

            val stored = bundleContents[bundleId] ?: emptyList()
            stored.forEachIndexed { index, item ->
                if (index < 9) inventory.setItemStack(index, item)
            }

            openBundles[player.uuid] = BundleSession(hand, inventory, bundleId)
            player.openInventory(inventory)
        }

        eventNode.addListener(InventoryCloseEvent::class.java) { event ->
            val player = event.player as? Player ?: return@addListener
            val session = openBundles.remove(player.uuid) ?: return@addListener

            val items = mutableListOf<ItemStack>()
            var totalWeight = 0
            for (slot in 0 until session.inventory.size) {
                val item = session.inventory.getItemStack(slot)
                if (item.isAir) continue
                val weight = totalWeight + item.amount()
                if (weight > 64) break
                items.add(item)
                totalWeight = weight
            }

            if (items.isEmpty()) {
                bundleContents.remove(session.bundleId)
            } else {
                bundleContents[session.bundleId] = items.toList()
            }
        }
    }

    override fun onDisable() {
        openBundles.clear()
        bundleContents.clear()
        super.onDisable()
    }
}
