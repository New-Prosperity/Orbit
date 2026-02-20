package me.nebula.orbit.mechanic.block

import me.nebula.orbit.mechanic.data.BlockDropData
import me.nebula.orbit.mechanic.food.addExhaustion
import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.customcontent.block.BlockStateAllocator
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

class BlockModule : OrbitModule("block") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val player = event.player
            val block = event.block
            val blockPos = event.blockPosition

            if (BlockStateAllocator.isAllocated(block)) return@addListener

            val requiresTool = block.registry()?.requiresTool() ?: false
            val tool = player.getItemInMainHand().get(DataComponents.TOOL)
            val hasCorrectTool = !requiresTool || tool?.isCorrectForDrops(block) == true

            if (hasCorrectTool) {
                val drop = BlockDropData[block]
                if (drop != null) {
                    val instance = player.instance ?: return@addListener
                    val center = Pos(blockPos.x() + 0.5, blockPos.y() + 0.5, blockPos.z() + 0.5)
                    val random = ThreadLocalRandom.current()

                    drop.drops.forEach { entry ->
                        if (random.nextFloat() > entry.chance) return@forEach
                        val itemEntity = ItemEntity(entry.itemStack)
                        itemEntity.setPickupDelay(Duration.ofMillis(500))
                        itemEntity.setInstance(instance, center)

                        itemEntity.scheduler().buildTask { itemEntity.remove() }
                            .delay(TaskSchedule.minutes(5))
                            .schedule()
                    }
                }
            }

            player.addExhaustion(0.005f)
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val blockPos = event.blockPosition
            val instance = event.player.instance ?: return@addListener
            val nearby = instance.getNearbyEntities(
                Pos(blockPos.x() + 0.5, blockPos.y() + 0.5, blockPos.z() + 0.5),
                1.0,
            )
            if (nearby.any { entity -> entity is Player }) {
                event.isCancelled = true
            }
        }
    }
}
