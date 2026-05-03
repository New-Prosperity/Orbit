package me.nebula.orbit.utils.customcontent.event

import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent

object CustomBlockBreakHandler {

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.isCancelled) return@addListener
            val customBlock = CustomBlockRegistry.fromVanillaBlock(event.block) ?: return@addListener
            val instance = event.player.instance ?: return@addListener
            event.isCancelled = true
            CustomBlockBreaker.execute(instance, event.blockPosition, customBlock)
        }
    }
}
