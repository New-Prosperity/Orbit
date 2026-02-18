package me.nebula.orbit.mechanic.death

import me.nebula.orbit.mechanic.food.clearExhaustion
import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerRespawnEvent
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

class DeathModule : OrbitModule("death") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val position = player.position
            val random = ThreadLocalRandom.current()

            for (slot in 0 until player.inventory.size) {
                val item = player.inventory.getItemStack(slot)
                if (item.isAir) continue

                val itemEntity = ItemEntity(item)
                itemEntity.setPickupDelay(Duration.ofSeconds(1))
                itemEntity.velocity = Vec(
                    random.nextDouble(-1.0, 1.0) * 3.0,
                    random.nextDouble(2.0, 5.0),
                    random.nextDouble(-1.0, 1.0) * 3.0,
                )
                itemEntity.setInstance(instance, position.add(0.0, 1.0, 0.0))

                itemEntity.scheduler().buildTask { itemEntity.remove() }
                    .delay(TaskSchedule.minutes(5))
                    .schedule()
            }
            player.inventory.clear()
        }

        eventNode.addListener(PlayerRespawnEvent::class.java) { event ->
            val player = event.player
            player.heal()
            player.food = 20
            player.foodSaturation = 5f
            player.clearExhaustion()
        }
    }
}
