package me.nebula.orbit.utils.snapshot

import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack

data class InventorySnapshot(
    val contents: Map<Int, ItemStack>,
    val food: Int,
    val health: Float,
    val experience: Float,
    val level: Int,
) {

    fun restore(player: Player) {
        player.inventory.clear()
        contents.forEach { (slot, item) ->
            player.inventory.setItemStack(slot, item)
        }
        player.food = food
        player.health = health
        player.exp = experience
        player.level = level
    }

    companion object {

        fun capture(player: Player): InventorySnapshot {
            val contents = mutableMapOf<Int, ItemStack>()
            for (slot in 0 until player.inventory.size) {
                val item = player.inventory.getItemStack(slot)
                if (!item.isAir) {
                    contents[slot] = item
                }
            }
            return InventorySnapshot(
                contents = contents.toMap(),
                food = player.food,
                health = player.health,
                experience = player.exp,
                level = player.level.toInt(),
            )
        }
    }
}

fun Player.captureSnapshot(): InventorySnapshot = InventorySnapshot.capture(this)

fun Player.restoreSnapshot(snapshot: InventorySnapshot) = snapshot.restore(this)
