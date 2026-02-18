package me.nebula.orbit.utils.hotbar

import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class HotbarSlot(
    val slot: Int,
    val item: ItemStack,
    val onClick: (Player) -> Unit = {},
)

class Hotbar(
    val name: String,
    val slots: Map<Int, HotbarSlot>,
    val clearOtherSlots: Boolean = true,
) {
    private val activePlayers = ConcurrentHashMap.newKeySet<UUID>()
    val eventNode = EventNode.all("hotbar:$name")

    init {
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (!activePlayers.contains(event.player.uuid)) return@addListener
            val heldSlot = event.player.heldSlot.toInt()
            slots[heldSlot]?.onClick?.invoke(event.player)
        }
    }

    fun apply(player: Player) {
        if (clearOtherSlots) {
            for (i in 0..8) {
                player.inventory.setItemStack(i, ItemStack.AIR)
            }
        }
        slots.forEach { (slot, entry) ->
            player.inventory.setItemStack(slot, entry.item)
        }
        activePlayers.add(player.uuid)
    }

    fun remove(player: Player) {
        activePlayers.remove(player.uuid)
        if (clearOtherSlots) {
            for (i in 0..8) {
                player.inventory.setItemStack(i, ItemStack.AIR)
            }
        } else {
            slots.keys.forEach { slot ->
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }
    }

    fun isActive(player: Player): Boolean = activePlayers.contains(player.uuid)

    fun install() {
        MinecraftServer.getGlobalEventHandler().addChild(eventNode)
    }

    fun uninstall() {
        MinecraftServer.getGlobalEventHandler().removeChild(eventNode)
        activePlayers.clear()
    }
}

class HotbarBuilder(val name: String) {
    private val slots = mutableMapOf<Int, HotbarSlot>()
    var clearOtherSlots: Boolean = true

    fun slot(slot: Int, item: ItemStack, onClick: (Player) -> Unit = {}) {
        require(slot in 0..8) { "Hotbar slot must be 0-8" }
        slots[slot] = HotbarSlot(slot, item, onClick)
    }

    fun build(): Hotbar = Hotbar(name, slots.toMap(), clearOtherSlots)
}

inline fun hotbar(name: String, block: HotbarBuilder.() -> Unit): Hotbar =
    HotbarBuilder(name).apply(block).build()
