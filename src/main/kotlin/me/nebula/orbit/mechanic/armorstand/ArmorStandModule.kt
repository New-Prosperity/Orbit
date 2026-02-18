package me.nebula.orbit.mechanic.armorstand

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.player.PlayerEntityInteractEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import kotlin.math.cos
import kotlin.math.sin

class ArmorStandModule : OrbitModule("armorstand") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.ARMOR_STAND) return@addListener

            val player = event.player
            val yaw = Math.toRadians(player.position.yaw().toDouble())
            val pitch = Math.toRadians(player.position.pitch().toDouble())

            val spawnPos = Pos(
                player.position.x() - sin(yaw) * 2.0,
                player.position.y(),
                player.position.z() + cos(yaw) * 2.0,
                (180f - player.position.yaw()),
                0f,
            )

            val armorStand = Entity(EntityType.ARMOR_STAND)
            armorStand.setInstance(player.instance!!, spawnPos)

            val slot = player.heldSlot.toInt()
            val item = player.inventory.getItemStack(slot)
            if (item.amount() > 1) {
                player.inventory.setItemStack(slot, item.withAmount(item.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }
        }

        eventNode.addListener(PlayerEntityInteractEvent::class.java) { event ->
            if (event.target.entityType != EntityType.ARMOR_STAND) return@addListener
            if (event.player.isSneaking) {
                event.target.remove()
            }
        }
    }
}
