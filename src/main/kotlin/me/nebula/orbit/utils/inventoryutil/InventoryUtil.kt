package me.nebula.orbit.utils.inventoryutil

import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

fun Player.hasItem(material: Material): Boolean =
    (0 until inventory.size).any { inventory.getItemStack(it).material() == material }

fun Player.countItem(material: Material): Int =
    (0 until inventory.size).sumOf { slot ->
        val item = inventory.getItemStack(slot)
        if (item.material() == material) item.amount() else 0
    }

fun Player.removeItem(material: Material, amount: Int = 1): Boolean {
    var remaining = amount
    for (i in 0 until inventory.size) {
        if (remaining <= 0) break
        val item = inventory.getItemStack(i)
        if (item.material() != material) continue
        val take = minOf(remaining, item.amount())
        remaining -= take
        if (take >= item.amount()) {
            inventory.setItemStack(i, ItemStack.AIR)
        } else {
            inventory.setItemStack(i, item.withAmount(item.amount() - take))
        }
    }
    return remaining <= 0
}

fun Player.giveItem(item: ItemStack): Boolean {
    return inventory.addItemStack(item)
}

fun Player.firstEmptySlot(): Int? =
    (0 until inventory.size).firstOrNull { inventory.getItemStack(it).isAir }

fun Player.clearInventory() {
    for (i in 0 until inventory.size) {
        inventory.setItemStack(i, ItemStack.AIR)
    }
}

fun Player.sortInventory() {
    val items = (0 until inventory.size)
        .map { inventory.getItemStack(it) }
        .filter { !it.isAir }
        .sortedBy { it.material().key().value() }

    clearInventory()
    items.forEachIndexed { index, item ->
        inventory.setItemStack(index, item)
    }
}

fun Player.swapSlots(slot1: Int, slot2: Int) {
    val item1 = inventory.getItemStack(slot1)
    val item2 = inventory.getItemStack(slot2)
    inventory.setItemStack(slot1, item2)
    inventory.setItemStack(slot2, item1)
}
