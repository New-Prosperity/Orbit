package me.nebula.orbit.utils.customcontent.event

import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent

object CustomBlockInteractHandler {

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.player.instance?.getBlock(event.blockPosition) ?: return@addListener
            if (CustomBlockRegistry.fromVanillaBlock(block) != null) {
                event.isCancelled = true
                event.setBlockingItemUse(false)
            }
        }
    }
}
