package me.nebula.orbit.mechanic.headdrop

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.translation.translate
import net.minestom.server.component.DataComponents
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration

class HeadDropModule : OrbitModule("head-drop") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            val victim = event.player
            val killer = victim.lastDamageSource?.let {
                (it as? net.minestom.server.entity.damage.EntityDamage)?.source as? Player
            } ?: return@addListener

            val instance = victim.instance ?: return@addListener
            val position = victim.position

            val skull = ItemStack.of(Material.PLAYER_HEAD)
                .with(DataComponents.CUSTOM_NAME, killer.translate("orbit.mechanic.head_drop.name", "victim" to victim.username))
                .with(DataComponents.LORE, listOf(killer.translate("orbit.mechanic.head_drop.lore", "killer" to killer.username)))

            val itemEntity = ItemEntity(skull)
            itemEntity.setPickupDelay(Duration.ofSeconds(1))
            itemEntity.setInstance(instance, position.add(0.0, 1.0, 0.0))

            itemEntity.scheduler().buildTask { itemEntity.remove() }
                .delay(net.minestom.server.timer.TaskSchedule.minutes(5))
                .schedule()
        }
    }
}
