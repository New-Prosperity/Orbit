package me.nebula.orbit.mechanic.projectile

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.EntityDamage
import net.minestom.server.event.item.PlayerBeginItemUseEvent
import net.minestom.server.event.item.PlayerCancelItemUseEvent

import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin

private val BOW_CHARGE_TAG = Tag.Long("mechanic:projectile:bow_charge_start").defaultValue(0L)

class ProjectileModule : OrbitModule("projectile") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBeginItemUseEvent::class.java) { event ->
            val material = event.itemStack.material()
            if (material == Material.BOW || material == Material.CROSSBOW) {
                event.player.setTag(BOW_CHARGE_TAG, System.currentTimeMillis())
            }
        }

        eventNode.addListener(PlayerCancelItemUseEvent::class.java) { event ->
            val player = event.player
            val material = event.itemStack.material()
            if (material != Material.BOW) return@addListener

            val chargeStart = player.getTag(BOW_CHARGE_TAG)
            if (chargeStart == 0L) return@addListener

            val chargeDuration = (System.currentTimeMillis() - chargeStart) / 1000f
            val power = (chargeDuration * chargeDuration + chargeDuration * 2f) / 3f
            val clampedPower = power.coerceIn(0f, 1f)

            if (clampedPower < 0.1f) return@addListener

            val arrow = Entity(EntityType.ARROW)
            val yaw = Math.toRadians(player.position.yaw().toDouble())
            val pitch = Math.toRadians(player.position.pitch().toDouble())
            val speed = clampedPower * 45.0

            arrow.velocity = Vec(
                -sin(yaw) * cos(pitch) * speed,
                -sin(pitch) * speed,
                cos(yaw) * cos(pitch) * speed,
            )

            arrow.setTag(Tag.Float("mechanic:projectile:damage"), 2f + clampedPower * 4f)
            arrow.setTag(Tag.Integer("mechanic:projectile:shooter"), player.entityId)

            arrow.setInstance(player.instance!!, player.position.add(0.0, player.eyeHeight, 0.0))

            arrow.scheduler().buildTask { arrow.remove() }
                .delay(TaskSchedule.seconds(60))
                .schedule()

            player.setTag(BOW_CHARGE_TAG, 0L)

            consumeArrow(player)
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
