package me.nebula.orbit.mechanic.loyalty

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.item.enchant.Enchantment
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule

private val TRIDENT_OWNER_TAG = Tag.Integer("mechanic:trident:owner_id")
private val LOYALTY_LEVEL_TAG = Tag.Integer("mechanic:loyalty:level").defaultValue(0)
private val LOYALTY_RETURNING_TAG = Tag.Boolean("mechanic:loyalty:returning").defaultValue(false)

class LoyaltyModule : OrbitModule("loyalty") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.TRIDENT) return@addListener

            val item = event.itemStack
            val enchantments = item.get(DataComponents.ENCHANTMENTS) ?: return@addListener
            val level = enchantments.level(Enchantment.LOYALTY)
            if (level <= 0) return@addListener

            val player = event.player
            val instance = player.instance ?: return@addListener

            player.scheduler().buildTask {
                instance.entities
                    .filter { it.entityType == EntityType.TRIDENT }
                    .filter { it.getTag(TRIDENT_OWNER_TAG) == player.entityId }
                    .filter { !it.getTag(LOYALTY_RETURNING_TAG) }
                    .forEach { trident ->
                        trident.setTag(LOYALTY_LEVEL_TAG, level)
                        scheduleReturn(trident, player, level)
                    }
            }.delay(TaskSchedule.tick(1)).schedule()
        }
    }

    private fun scheduleReturn(trident: Entity, owner: Player, level: Int) {
        val returnDelay = ((4 - level.coerceAtMost(3)) * 20).coerceAtLeast(10)

        trident.scheduler().buildTask {
            if (trident.isRemoved || owner.isRemoved) return@buildTask
            trident.setTag(LOYALTY_RETURNING_TAG, true)

            trident.scheduler().buildTask {
                if (trident.isRemoved || owner.isRemoved) {
                    trident.remove()
                    return@buildTask
                }

                val direction = owner.position.asVec()
                    .add(0.0, owner.eyeHeight, 0.0)
                    .sub(trident.position.asVec())
                val distance = direction.length()

                if (distance < 1.5) {
                    trident.remove()
                    val slot = findEmptySlot(owner)
                    if (slot >= 0) {
                        owner.inventory.setItemStack(slot, ItemStack.of(Material.TRIDENT))
                    }
                    return@buildTask
                }

                val speed = (level * 12.0).coerceAtLeast(15.0)
                trident.velocity = direction.normalize().mul(speed)
            }.repeat(TaskSchedule.tick(1)).schedule()
        }.delay(TaskSchedule.tick(returnDelay)).schedule()
    }

    private fun findEmptySlot(player: Player): Int {
        for (slot in 0 until player.inventory.size) {
            if (player.inventory.getItemStack(slot).isAir) return slot
        }
        return -1
    }
}
