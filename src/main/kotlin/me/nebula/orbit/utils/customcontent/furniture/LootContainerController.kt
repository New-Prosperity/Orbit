package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.orbit.translation.translate
import me.nebula.orbit.utils.chat.miniMessage
import net.kyori.adventure.text.Component
import net.minestom.server.entity.Player
import net.minestom.server.inventory.Inventory
import net.minestom.server.inventory.InventoryType

object LootContainerController {

    fun onClick(
        player: Player,
        furniture: FurnitureInstance,
        config: FurnitureInteraction.LootContainer,
    ) {
        val inventory = FurnitureInstanceState.inventoryOf(furniture.uuid)
            ?: createInventoryFor(player, furniture, config).also { FurnitureInstanceState.setInventory(furniture.uuid, it) }
        player.openInventory(inventory)
    }

    private fun createInventoryFor(
        player: Player,
        furniture: FurnitureInstance,
        config: FurnitureInteraction.LootContainer,
    ): Inventory {
        val inventoryType = when (config.rows) {
            1 -> InventoryType.CHEST_1_ROW
            2 -> InventoryType.CHEST_2_ROW
            3 -> InventoryType.CHEST_3_ROW
            4 -> InventoryType.CHEST_4_ROW
            5 -> InventoryType.CHEST_5_ROW
            6 -> InventoryType.CHEST_6_ROW
            else -> InventoryType.CHEST_3_ROW
        }
        val title: Component = config.titleKey
            ?.let { player.translate(it) }
            ?: miniMessage.deserialize("<gray>Container")
        return Inventory(inventoryType, title)
    }
}
