package me.nebula.orbit.mechanic.crossbow

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin

private val LOADING_TAG = Tag.Long("mechanic:crossbow:loading_start").defaultValue(0L)
private val LOADED_TAG = Tag.Boolean("mechanic:crossbow:loaded").defaultValue(false)

class CrossbowModule : OrbitModule("crossbow") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBeginItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.CROSSBOW) return@addListener
            val player = event.player

            if (player.getTag(LOADED_TAG)) {
                fireCrossbow(player)
                player.setTag(LOADED_TAG, false)
            } else {
                player.setTag(LOADING_TAG, System.currentTimeMillis())
            }
        }

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            if (event.itemStack.material() != Material.CROSSBOW) return@addListener

            val player = event.player
            val loadStart = player.getTag(LOADING_TAG)
            if (loadStart == 0L) return@addListener

            val loadDuration = System.currentTimeMillis() - loadStart
            if (loadDuration >= 1250) {
                player.setTag(LOADED_TAG, true)
            }
            player.setTag(LOADING_TAG, 0L)
        }
    }

    private fun fireCrossbow(player: Player) {
        val hasArrow = (0 until player.inventory.size).any {
            player.inventory.getItemStack(it).material() == Material.ARROW
        }
        if (!hasArrow) return

        val arrow = Entity(EntityType.ARROW)
        arrow.setTag(Tag.Integer("mechanic:crossbow:shooter"), player.entityId)
        arrow.setTag(Tag.Float("mechanic:crossbow:damage"), 9f)
        arrow.setNoGravity(false)

        val yaw = Math.toRadians(player.position.yaw().toDouble())
        val pitch = Math.toRadians(player.position.pitch().toDouble())
        val speed = 50.0
        arrow.velocity = Vec(
            -sin(yaw) * cos(pitch) * speed,
            -sin(pitch) * speed,
            cos(yaw) * cos(pitch) * speed,
        )

        arrow.setInstance(player.instance!!, player.position.add(0.0, player.eyeHeight, 0.0))

        consumeArrow(player)

        arrow.scheduler().buildTask {
            checkArrowCollision(arrow)
        }.repeat(TaskSchedule.tick(1)).schedule()

        arrow.scheduler().buildTask {
            arrow.remove()
        }.delay(TaskSchedule.seconds(60)).schedule()
    }

    private fun checkArrowCollision(arrow: Entity) {
        if (arrow.isRemoved) return
        val instance = arrow.instance ?: return
        val pos = arrow.position

        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR) {
            arrow.remove()
            return
        }

        val shooterId = arrow.getTag(Tag.Integer("mechanic:crossbow:shooter"))
        val damage = arrow.getTag(Tag.Float("mechanic:crossbow:damage")) ?: 9f
        instance.getNearbyEntities(pos, 1.0).forEach { entity ->
            if (entity == arrow) return@forEach
            if (entity.entityId == shooterId) return@forEach
            if (entity is LivingEntity) {
                entity.damage(EntityDamage(arrow, damage))
            }
            arrow.remove()
            return
        }
    }

    private fun consumeArrow(player: Player) {
        for (slot in 0 until player.inventory.size) {
            val item = player.inventory.getItemStack(slot)
            if (item.material() == Material.ARROW) {
                if (item.amount() > 1) {
                    player.inventory.setItemStack(slot, item.withAmount(item.amount() - 1))
                } else {
                    player.inventory.setItemStack(slot, net.minestom.server.item.ItemStack.AIR)
                }
                return
            }
        }
    }
}
