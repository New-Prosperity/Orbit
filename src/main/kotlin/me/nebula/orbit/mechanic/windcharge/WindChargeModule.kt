package me.nebula.orbit.mechanic.windcharge

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin

private val WIND_CHARGE_OWNER_TAG = Tag.Integer("mechanic:windcharge:owner").defaultValue(-1)
private const val KNOCKBACK_RADIUS = 2.5
private const val KNOCKBACK_STRENGTH = 12.0

class WindChargeModule : OrbitModule("wind-charge") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val player = event.player
            val item = event.itemStack
            if (item.material() != Material.WIND_CHARGE) return@addListener

            val slot = player.heldSlot.toInt()
            val held = player.inventory.getItemStack(slot)
            if (held.amount() > 1) {
                player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }

            launchWindCharge(player)
        }
    }

    private fun launchWindCharge(player: Player) {
        val projectile = Entity(EntityType.WIND_CHARGE)
        projectile.setTag(WIND_CHARGE_OWNER_TAG, player.entityId)
        projectile.setNoGravity(false)

        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val pitch = Math.toRadians(player.position.pitch().toDouble())
        val speed = 35.0
        projectile.velocity = Vec(
            -sin(yaw) * cos(pitch) * speed,
            -sin(pitch) * speed,
            cos(yaw) * cos(pitch) * speed,
        )

        projectile.setInstance(player.instance!!, player.position.add(0.0, player.eyeHeight, 0.0))

        projectile.scheduler().buildTask {
            checkCollision(projectile)
        }.repeat(TaskSchedule.tick(1)).schedule()

        projectile.scheduler().buildTask {
            projectile.remove()
        }.delay(TaskSchedule.seconds(30)).schedule()
    }

    private fun checkCollision(projectile: Entity) {
        if (projectile.isRemoved) return
        val instance = projectile.instance ?: return
        val pos = projectile.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR && block != Block.CAVE_AIR && block != Block.VOID_AIR) {
            explode(projectile)
            return
        }

        val ownerId = projectile.getTag(WIND_CHARGE_OWNER_TAG)
        instance.getNearbyEntities(pos, 1.0).forEach { entity ->
            if (entity == projectile) return@forEach
            if (entity.entityId == ownerId) return@forEach
            if (entity is LivingEntity) {
                explode(projectile)
                return
            }
        }
    }

    private fun explode(projectile: Entity) {
        val instance = projectile.instance ?: return
        val pos = projectile.position
        val ownerId = projectile.getTag(WIND_CHARGE_OWNER_TAG)

        instance.getNearbyEntities(pos, KNOCKBACK_RADIUS).forEach { entity ->
            if (entity == projectile) return@forEach
            if (entity !is LivingEntity) return@forEach

            val direction = entity.position.asVec().sub(pos.asVec())
            val distance = direction.length()
            if (distance < 0.1) return@forEach

            val strength = KNOCKBACK_STRENGTH * (1.0 - distance / KNOCKBACK_RADIUS).coerceAtLeast(0.2)
            val knockback = direction.normalize().mul(strength).withY(direction.normalize().y().coerceAtLeast(0.3) * strength)
            entity.velocity = entity.velocity.add(knockback)
        }

        projectile.remove()
    }
}
