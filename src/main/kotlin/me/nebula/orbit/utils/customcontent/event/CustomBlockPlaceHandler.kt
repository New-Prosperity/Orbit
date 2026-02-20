package me.nebula.orbit.utils.customcontent.event

import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import me.nebula.orbit.utils.customcontent.item.CustomItemRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.item.ItemStack

object CustomBlockPlaceHandler {

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val player = event.player
            val heldItem = player.getItemInMainHand()
            val cmd = heldItem.get(DataComponents.CUSTOM_MODEL_DATA) ?: return@addListener
            val cmdValue = cmd.floats().firstOrNull()?.toInt() ?: return@addListener
            val customItem = CustomItemRegistry.byCustomModelData(cmdValue) ?: return@addListener
            val customBlock = CustomBlockRegistry.fromItemId(customItem.id) ?: return@addListener

            event.isCancelled = true

            val instance = player.instance ?: return@addListener
            val pos = event.blockPosition
            instance.setBlock(pos, customBlock.allocatedState)

            if (player.gameMode != GameMode.CREATIVE) {
                val newItem = if (heldItem.amount() > 1) heldItem.withAmount(heldItem.amount() - 1)
                else ItemStack.AIR
                player.setItemInMainHand(newItem)
            }

            instance.playSound(
                Sound.sound(Key.key("minecraft", customBlock.placeSound), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
