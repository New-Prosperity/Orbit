package me.nebula.orbit.mechanic.looting

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom

private val LAST_ATTACKER_TAG = Tag.Integer("mechanic:looting:last_attacker").defaultValue(-1)
private val LOOTING_LEVEL_TAG = Tag.Integer("mechanic:looting:level").defaultValue(0)

class LootingModule : OrbitModule("looting") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityAttackEvent::class.java) { event ->
            val attacker = event.entity as? Player ?: return@addListener
            val target = event.target as? LivingEntity ?: return@addListener
            if (target is Player) return@addListener

            val item = attacker.getItemInMainHand()
            if (item.isAir) return@addListener

            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.LOOTING)
            if (level <= 0) return@addListener

            target.setTag(LAST_ATTACKER_TAG, attacker.entityId)
            target.setTag(LOOTING_LEVEL_TAG, level)

            target.scheduler().buildTask {
                if (!target.isRemoved && target.isDead) {
                    spawnBonusLoot(target)
                }
            }.delay(TaskSchedule.tick(1)).schedule()
        }
    }

    private fun spawnBonusLoot(target: LivingEntity) {
        val level = target.getTag(LOOTING_LEVEL_TAG)
        if (level <= 0) return

        val instance = target.instance ?: return
        val pos = target.position
        val random = ThreadLocalRandom.current()
        val bonusCount = random.nextInt(0, level + 1)
        if (bonusCount <= 0) return

        val dropMaterial = when {
            target.entityType.name().contains("zombie", ignoreCase = true) -> Material.ROTTEN_FLESH
            target.entityType.name().contains("skeleton", ignoreCase = true) -> Material.BONE
            target.entityType.name().contains("spider", ignoreCase = true) -> Material.STRING
            target.entityType.name().contains("creeper", ignoreCase = true) -> Material.GUNPOWDER
            target.entityType.name().contains("enderman", ignoreCase = true) -> Material.ENDER_PEARL
            target.entityType.name().contains("pig", ignoreCase = true) -> Material.PORKCHOP
            target.entityType.name().contains("cow", ignoreCase = true) -> Material.BEEF
            target.entityType.name().contains("chicken", ignoreCase = true) -> Material.CHICKEN
            target.entityType.name().contains("sheep", ignoreCase = true) -> Material.MUTTON
            else -> Material.ROTTEN_FLESH
        }

        repeat(bonusCount) {
            val itemEntity = ItemEntity(ItemStack.of(dropMaterial))
            itemEntity.setPickupDelay(Duration.ofMillis(500))
            itemEntity.setInstance(instance, Pos(pos.x(), pos.y() + 0.5, pos.z()))

            itemEntity.scheduler().buildTask { itemEntity.remove() }
                .delay(TaskSchedule.minutes(5))
                .schedule()
        }
    }
}
