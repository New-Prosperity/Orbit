package me.nebula.orbit.mechanic.firework

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule

class FireworkModule : OrbitModule("firework") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.FIREWORK_ROCKET) return@addListener
            val player = event.player
            val instance = player.instance ?: return@addListener

            val firework = Entity(EntityType.FIREWORK_ROCKET)
            firework.setInstance(instance, player.position.add(0.0, 1.0, 0.0))
            firework.velocity = Vec(0.0, 20.0, 0.0)

            firework.scheduler().buildTask {
                firework.remove()
            }.delay(TaskSchedule.tick(30 + (Math.random() * 20).toInt())).schedule()

            val slot = player.heldSlot.toInt()
            val held = player.getItemInMainHand()
            if (held.amount() > 1) {
                player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val held = event.player.getItemInMainHand()
            if (held.material() != Material.FIREWORK_ROCKET) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val firework = Entity(EntityType.FIREWORK_ROCKET)
            firework.setInstance(instance, Pos(pos.x() + 0.5, pos.y() + 1.0, pos.z() + 0.5))
            firework.velocity = Vec(0.0, 20.0, 0.0)

            firework.scheduler().buildTask {
                firework.remove()
            }.delay(TaskSchedule.tick(30 + (Math.random() * 20).toInt())).schedule()

            val slot = event.player.heldSlot.toInt()
            if (held.amount() > 1) {
                event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                event.player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }
    }
}
