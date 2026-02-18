package me.nebula.orbit.mechanic.trident

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin

private val CHARGE_TAG = Tag.Long("mechanic:trident:charge_start").defaultValue(0L)
private val TRIDENT_OWNER_TAG = Tag.Integer("mechanic:trident:owner_id")

class TridentModule : OrbitModule("trident") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBeginItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.TRIDENT) return@addListener
            event.player.setTag(CHARGE_TAG, System.currentTimeMillis())
        }

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.TRIDENT) return@addListener

            val player = event.player
            val chargeStart = player.getTag(CHARGE_TAG)
            if (chargeStart == 0L) return@addListener

            val chargeDuration = System.currentTimeMillis() - chargeStart
            if (chargeDuration < 200) return@addListener

            launchTrident(player)
            player.setTag(CHARGE_TAG, 0L)
        }
    }

    private fun launchTrident(player: Player) {
        val trident = Entity(EntityType.TRIDENT)
        trident.setTag(TRIDENT_OWNER_TAG, player.entityId)
        trident.setNoGravity(false)

        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val pitch = Math.toRadians(player.position.pitch().toDouble())
        val speed = 40.0
        trident.velocity = Vec(
            -sin(yaw) * cos(pitch) * speed,
            -sin(pitch) * speed,
            cos(yaw) * cos(pitch) * speed,
        )

        trident.setInstance(player.instance!!, player.position.add(0.0, player.eyeHeight, 0.0))

        val slot = player.heldSlot.toInt()
        val item = player.inventory.getItemStack(slot)
        if (item.amount() > 1) {
            player.inventory.setItemStack(slot, item.withAmount(item.amount() - 1))
        } else {
            player.inventory.setItemStack(slot, ItemStack.AIR)
        }

        trident.scheduler().buildTask {
            checkTridentCollision(trident)
        }.repeat(TaskSchedule.tick(1)).schedule()

        trident.scheduler().buildTask {
            trident.remove()
        }.delay(TaskSchedule.seconds(60)).schedule()
    }

    private fun checkTridentCollision(trident: Entity) {
        if (trident.isRemoved) return
        val instance = trident.instance ?: return
        val pos = trident.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR) {
            trident.remove()
            return
        }

        val ownerId = trident.getTag(TRIDENT_OWNER_TAG)
        instance.getNearbyEntities(pos, 1.0).forEach { entity ->
            if (entity == trident) return@forEach
            if (entity.entityId == ownerId) return@forEach
            if (entity is LivingEntity) {
                entity.damage(EntityDamage(trident, 8f))
                val knockback = Vec(
                    entity.position.x() - pos.x(),
                    0.4,
                    entity.position.z() - pos.z(),
                ).normalize().mul(8.0)
                entity.velocity = entity.velocity.add(knockback)
            }
            trident.remove()
            return
        }
    }
}
