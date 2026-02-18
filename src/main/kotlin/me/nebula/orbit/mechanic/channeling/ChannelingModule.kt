package me.nebula.orbit.mechanic.channeling

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.instance.Weather
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule

private val THUNDER_STATE_TAG = Tag.Byte("mechanic:weather:state")
private const val THUNDER: Byte = 2

class ChannelingModule : OrbitModule("channeling") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target as? LivingEntity ?: return@addListener

            val item = attacker.getItemInMainHand()
            if (item.material() != Material.TRIDENT) return@addListener

            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.CHANNELING)
            if (level <= 0) return@addListener

            val instance = attacker.instance ?: return@addListener
            val weatherState = instance.getTag(THUNDER_STATE_TAG) ?: return@addListener
            if (weatherState != THUNDER) return@addListener

            val targetPos = target.position

            val lightning = Entity(EntityType.LIGHTNING_BOLT)
            lightning.setInstance(instance, targetPos)

            target.damage(EntityDamage(attacker, 5f))
            target.entityMeta.setOnFire(true)

            target.scheduler().buildTask {
                target.entityMeta.setOnFire(false)
            }.delay(TaskSchedule.tick(100)).schedule()

            lightning.scheduler().buildTask {
                lightning.remove()
            }.delay(TaskSchedule.tick(20)).schedule()
        }
    }
}
