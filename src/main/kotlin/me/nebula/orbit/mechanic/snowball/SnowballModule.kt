package me.nebula.orbit.mechanic.snowball

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin

private val PROJECTILE_OWNER_TAG = Tag.Integer("mechanic:snowball:owner_id")
private val PROJECTILE_TYPE_TAG = Tag.String("mechanic:snowball:type")

class SnowballModule : OrbitModule("snowball") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val material = event.itemStack.material()
            val entityType = when (material) {
                Material.SNOWBALL -> EntityType.SNOWBALL
                Material.EGG -> EntityType.EGG
                else -> return@addListener
            }

            val player = event.player
            val projectile = Entity(entityType)
            projectile.setTag(PROJECTILE_OWNER_TAG, player.entityId)
            projectile.setTag(PROJECTILE_TYPE_TAG, material.key().asString())
            projectile.setNoGravity(false)

            val yaw = Math.toRadians(player.position.yaw().toDouble())
            val pitch = Math.toRadians(player.position.pitch().toDouble())
            val speed = 30.0
            projectile.velocity = Vec(
                -sin(yaw) * cos(pitch) * speed,
                -sin(pitch) * speed,
                cos(yaw) * cos(pitch) * speed,
            )

            projectile.setInstance(player.instance!!, player.position.add(0.0, player.eyeHeight, 0.0))

            val slot = player.heldSlot.toInt()
            val item = player.inventory.getItemStack(slot)
            if (item.amount() > 1) {
                player.inventory.setItemStack(slot, item.withAmount(item.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }

            projectile.scheduler().buildTask {
                checkCollision(projectile)
            }.repeat(TaskSchedule.tick(1)).schedule()

            projectile.scheduler().buildTask {
                projectile.remove()
            }.delay(TaskSchedule.seconds(30)).schedule()
        }
    }

    private fun checkCollision(projectile: Entity) {
        if (projectile.isRemoved) return
        val instance = projectile.instance ?: return
        val pos = projectile.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR) {
            onHit(projectile, null)
            return
        }

        val ownerId = projectile.getTag(PROJECTILE_OWNER_TAG)
        instance.getNearbyEntities(pos, 1.0).forEach { entity ->
            if (entity == projectile) return@forEach
            if (entity.entityId == ownerId) return@forEach
            onHit(projectile, entity)
            return
        }
    }

    private fun onHit(projectile: Entity, target: Entity?) {
        if (projectile.isRemoved) return
        val type = projectile.getTag(PROJECTILE_TYPE_TAG)

        if (target is LivingEntity) {
            val knockback = Vec(
                target.position.x() - projectile.position.x(),
                0.3,
                target.position.z() - projectile.position.z(),
            ).normalize().mul(6.0)
            target.velocity = target.velocity.add(knockback)

            if (type == "minecraft:snowball") {
                target.damage(EntityDamage(projectile, 0f))
            } else {
                target.damage(EntityDamage(projectile, 0f))
                if (target.entityType == EntityType.CHICKEN && kotlin.random.Random.nextFloat() < 0.125f) {
                    val chicken = Entity(EntityType.CHICKEN)
                    chicken.setInstance(target.instance!!, target.position)
                }
            }
        }

        projectile.remove()
    }
}
