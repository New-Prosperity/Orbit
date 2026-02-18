package me.nebula.orbit.mechanic.fireaspect

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.timer.TaskSchedule

class FireAspectModule : OrbitModule("fire-aspect") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target as? LivingEntity ?: return@addListener

            val item = attacker.getItemInMainHand()
            if (item.isAir) return@addListener

            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.FIRE_ASPECT)
            if (level <= 0) return@addListener

            target.entityMeta.setOnFire(true)

            target.scheduler().buildTask {
                target.entityMeta.setOnFire(false)
            }.delay(TaskSchedule.tick(level * 80)).schedule()
        }
    }
}
