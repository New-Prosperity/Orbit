package me.nebula.orbit.mechanic.enderpearl

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.item.PlayerFinishItemUseEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin

private val PEARL_OWNER_TAG = Tag.String("mechanic:enderpearl:owner")

class EnderPearlModule : OrbitModule("enderpearl") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.itemStack.material() != Material.ENDER_PEARL) return@addListener

            val player = event.player
            val pearl = Entity(EntityType.ENDER_PEARL)
            pearl.setTag(PEARL_OWNER_TAG, player.uuid.toString())
            pearl.setNoGravity(false)

            val yaw = Math.toRadians(player.position.yaw().toDouble())
            val pitch = Math.toRadians(player.position.pitch().toDouble())
            val speed = 30.0
            pearl.velocity = Vec(
                -sin(yaw) * cos(pitch) * speed,
                -sin(pitch) * speed,
                cos(yaw) * cos(pitch) * speed,
            )

            pearl.setInstance(player.instance!!, player.position.add(0.0, player.eyeHeight, 0.0))

            val slot = player.heldSlot.toInt()
            val item = player.inventory.getItemStack(slot)
            if (item.amount() > 1) {
                player.inventory.setItemStack(slot, item.withAmount(item.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, ItemStack.AIR)
            }

            pearl.scheduler().buildTask {
                teleportOwner(pearl)
            }.delay(TaskSchedule.seconds(5)).schedule()

            pearl.scheduler().buildTask {
                checkLanded(pearl)
            }.repeat(TaskSchedule.tick(1)).schedule()
        }
    }

    private fun checkLanded(pearl: Entity) {
        if (pearl.isRemoved) return
        val instance = pearl.instance ?: return

        val pos = pearl.position
        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block != Block.AIR) {
            teleportOwner(pearl)
        }
    }

    private fun teleportOwner(pearl: Entity) {
        if (pearl.isRemoved) return
        val ownerUuid = pearl.getTag(PEARL_OWNER_TAG) ?: run { pearl.remove(); return }
        val instance = pearl.instance ?: run { pearl.remove(); return }
        val uuid = java.util.UUID.fromString(ownerUuid)

        val player = instance.players.find { it.uuid == uuid }
        if (player != null) {
            player.teleport(Pos(pearl.position.x(), pearl.position.y(), pearl.position.z(), player.position.yaw(), player.position.pitch()))
            player.damage(DamageType.FALL, 5f)
        }
        pearl.remove()
    }
}
