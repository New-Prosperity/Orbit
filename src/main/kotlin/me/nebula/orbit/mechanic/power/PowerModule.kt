package me.nebula.orbit.mechanic.power

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.tag.Tag

private val POWER_LEVEL_TAG = Tag.Integer("mechanic:power:level").defaultValue(0)
private val ARROW_DAMAGE_TAG = Tag.Float("mechanic:projectile:damage")
private val SHOOTER_TAG = Tag.Integer("mechanic:projectile:shooter")

class PowerModule : OrbitModule("power") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.BOW) return@addListener

            val item = event.itemStack
            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val powerLevel = enchantments.level(Enchantment.POWER)
            if (powerLevel <= 0) return@addListener

            val player = event.player
            val instance = player.instance ?: return@addListener

            player.scheduler().buildTask {
                val shooterId = player.entityId
                instance.entities
                    .filter { it.entityType == EntityType.ARROW }
                    .filter { it.getTag(SHOOTER_TAG) == shooterId }
                    .filter { it.getTag(POWER_LEVEL_TAG) == 0 }
                    .forEach { arrow ->
                        arrow.setTag(POWER_LEVEL_TAG, powerLevel)
                        val baseDamage = arrow.getTag(ARROW_DAMAGE_TAG) ?: 6f
                        val bonusDamage = 0.5f * (powerLevel + 1)
                        arrow.setTag(ARROW_DAMAGE_TAG, baseDamage + bonusDamage)
                    }
            }.delay(net.minestom.server.timer.TaskSchedule.tick(1)).schedule()
        }
    }
}
