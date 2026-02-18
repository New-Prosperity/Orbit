package me.nebula.orbit.mechanic.breeding

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule

private val LOVE_MODE_TAG = Tag.Long("mechanic:breeding:love_start").defaultValue(0L)
private val BREED_COOLDOWN_TAG = Tag.Long("mechanic:breeding:cooldown").defaultValue(0L)

private val BREEDING_FOOD = mapOf(
    EntityType.COW to setOf(Material.WHEAT),
    EntityType.SHEEP to setOf(Material.WHEAT),
    EntityType.PIG to setOf(Material.CARROT, Material.POTATO, Material.BEETROOT),
    EntityType.CHICKEN to setOf(Material.WHEAT_SEEDS, Material.MELON_SEEDS, Material.PUMPKIN_SEEDS, Material.BEETROOT_SEEDS),
    EntityType.RABBIT to setOf(Material.CARROT, Material.GOLDEN_CARROT, Material.DANDELION),
    EntityType.HORSE to setOf(Material.GOLDEN_APPLE, Material.GOLDEN_CARROT),
    EntityType.WOLF to setOf(Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.COOKED_CHICKEN, Material.COOKED_MUTTON),
    EntityType.CAT to setOf(Material.COD, Material.SALMON),
)

class BreedingModule : OrbitModule("breeding") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            val entity = event.target as? EntityCreature ?: return@addListener
            val entityType = entity.entityType
            val validFoods = BREEDING_FOOD[entityType] ?: return@addListener
            val heldItem = event.player.getItemInMainHand()

            if (heldItem.material() !in validFoods) return@addListener

            val now = System.currentTimeMillis()
            val cooldown = entity.getTag(BREED_COOLDOWN_TAG)
            if (now - cooldown < 300_000L) return@addListener

            entity.setTag(LOVE_MODE_TAG, now)
            consumeItem(event.player)
            tryBreed(entity, entityType)
        }
    }

    private fun consumeItem(player: Player) {
        val item = player.getItemInMainHand()
        if (item.amount() > 1) {
            player.setItemInMainHand(item.withAmount(item.amount() - 1))
        } else {
            player.setItemInMainHand(net.minestom.server.item.ItemStack.AIR)
        }
    }

    private fun tryBreed(entity: EntityCreature, type: EntityType) {
        val instance = entity.instance ?: return
        val pos = entity.position

        val partner = instance.getNearbyEntities(pos, 8.0)
            .filterIsInstance<EntityCreature>()
            .filter { it !== entity && it.entityType == type }
            .firstOrNull { System.currentTimeMillis() - it.getTag(LOVE_MODE_TAG) < 30_000L }
            ?: return

        val now = System.currentTimeMillis()
        entity.setTag(LOVE_MODE_TAG, 0L)
        entity.setTag(BREED_COOLDOWN_TAG, now)
        partner.setTag(LOVE_MODE_TAG, 0L)
        partner.setTag(BREED_COOLDOWN_TAG, now)

        val baby = EntityCreature(type)
        baby.addAIGroup(listOf(RandomStrollGoal(baby, 5)), emptyList())
        baby.setBoundingBox(entity.boundingBox.width() * 0.5, entity.boundingBox.height() * 0.5, entity.boundingBox.depth() * 0.5)
        baby.setInstance(instance, Pos(
            (pos.x() + partner.position.x()) / 2,
            pos.y(),
            (pos.z() + partner.position.z()) / 2,
        ))

        baby.scheduler().buildTask {
            baby.setBoundingBox(entity.boundingBox.width(), entity.boundingBox.height(), entity.boundingBox.depth())
        }.delay(TaskSchedule.minutes(20)).schedule()
    }
}
