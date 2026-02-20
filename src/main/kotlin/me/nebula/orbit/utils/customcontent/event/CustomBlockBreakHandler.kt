package me.nebula.orbit.utils.customcontent.event

import me.nebula.orbit.utils.customcontent.block.CustomBlockDrops
import me.nebula.orbit.utils.customcontent.block.CustomBlockRegistry
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.timer.TaskSchedule
import java.time.Duration

object CustomBlockBreakHandler {

    fun install(eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val customBlock = CustomBlockRegistry.fromVanillaBlock(event.block) ?: return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val center = Pos(pos.x() + 0.5, pos.y() + 0.25, pos.z() + 0.5)

            val drops: List<ItemStack> = when (val d = customBlock.drops) {
                is CustomBlockDrops.SelfDrop -> listOf(customBlock.item().createStack())
                is CustomBlockDrops.LootTableDrop -> d.lootTable.roll()
            }

            drops.forEach { stack ->
                val itemEntity = ItemEntity(stack)
                itemEntity.setPickupDelay(Duration.ofMillis(500))
                itemEntity.setInstance(instance, center)
                itemEntity.scheduler().buildTask { itemEntity.remove() }
                    .delay(TaskSchedule.minutes(5))
                    .schedule()
            }

            instance.playSound(
                Sound.sound(Key.key("minecraft", customBlock.breakSound), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
