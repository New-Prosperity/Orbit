package me.nebula.orbit.mechanic.flame

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule

private val FLAME_TAG = Tag.Boolean("mechanic:flame:enabled").defaultValue(false)
private val SHOOTER_TAG = Tag.Integer("mechanic:projectile:shooter")

class FlameModule : OrbitModule("flame") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.BOW) return@addListener

            val item = event.itemStack
            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val flameLevel = enchantments.level(Enchantment.FLAME)
            if (flameLevel <= 0) return@addListener

            val player = event.player
            val instance = player.instance ?: return@addListener

            player.scheduler().buildTask {
                val shooterId = player.entityId
                instance.entities
                    .filter { it.entityType == EntityType.ARROW }
                    .filter { it.getTag(SHOOTER_TAG) == shooterId }
                    .filter { !it.getTag(FLAME_TAG) }
                    .forEach { arrow ->
                        arrow.setTag(FLAME_TAG, true)
                        arrow.entityMeta.setOnFire(true)
                        trackFlameArrow(arrow)
                    }
            }.delay(TaskSchedule.tick(1)).schedule()
        }
    }

    private fun trackFlameArrow(arrow: Entity) {
        arrow.scheduler().buildTask {
            if (arrow.isRemoved) return@buildTask
            val instance = arrow.instance ?: return@buildTask

            instance.getNearbyEntities(arrow.position, 1.0).forEach { entity ->
                if (entity == arrow) return@forEach
                if (entity.entityId == (arrow.getTag(SHOOTER_TAG) ?: -1)) return@forEach
                if (entity is LivingEntity) {
                    entity.entityMeta.setOnFire(true)
                    entity.scheduler().buildTask {
                        entity.entityMeta.setOnFire(false)
                    }.delay(TaskSchedule.tick(100)).schedule()
                }
            }
        }.repeat(TaskSchedule.tick(1)).schedule()
    }
}
