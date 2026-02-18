package me.nebula.orbit.mechanic.itemdrop

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.item.ItemDropEvent
import net.minestom.server.event.item.PickupItemEvent
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import kotlin.math.cos
import kotlin.math.sin

class ItemDropModule : OrbitModule("item-drop") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(ItemDropEvent::class.java) { event ->
            val player = event.player
            val itemStack = event.itemStack
            if (itemStack.isAir) return@addListener

            val itemEntity = ItemEntity(itemStack)
            itemEntity.setPickupDelay(Duration.ofSeconds(2))

            val yaw = Math.toRadians(player.position.yaw().toDouble())
            val pitch = Math.toRadians(player.position.pitch().toDouble())
            val speed = 4.0
            itemEntity.velocity = Vec(
                -sin(yaw) * cos(pitch) * speed,
                -sin(pitch) * speed + 2.0,
                cos(yaw) * cos(pitch) * speed,
            )

            itemEntity.setInstance(player.instance!!, player.position.add(0.0, player.eyeHeight, 0.0))

            itemEntity.scheduler().buildTask { itemEntity.remove() }
                .delay(TaskSchedule.minutes(5))
                .schedule()
        }

        eventNode.addListener(PickupItemEvent::class.java) { event ->
            val entity = event.livingEntity
            if (entity !is Player) {
                event.isCancelled = true
                return@addListener
            }
            if (!entity.inventory.addItemStack(event.itemStack)) {
                event.isCancelled = true
            }
        }
    }
}
