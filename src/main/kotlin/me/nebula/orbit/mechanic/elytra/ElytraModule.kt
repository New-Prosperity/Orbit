package me.nebula.orbit.mechanic.elytra

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.component.DataComponents
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EquipmentSlot
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import kotlin.math.cos
import kotlin.math.sin

private val GLIDING_TAG = Tag.Boolean("mechanic:elytra:gliding").defaultValue(false)

class ElytraModule : OrbitModule("elytra") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            val player = event.player
            if (event.itemStack.material() != Material.FIREWORK_ROCKET) return@addListener
            if (!player.getTag(GLIDING_TAG)) return@addListener

            val yaw = Math.toRadians(player.position.yaw().toDouble())
            val pitch = Math.toRadians(player.position.pitch().toDouble())
            val boost = 30.0
            player.velocity = player.velocity.add(
                Vec(
                    -sin(yaw) * cos(pitch) * boost,
                    -sin(pitch) * boost,
                    cos(yaw) * cos(pitch) * boost,
                ),
            )

            val slot = player.heldSlot.toInt()
            val item = player.inventory.getItemStack(slot)
            if (item.amount() > 1) {
                player.inventory.setItemStack(slot, item.withAmount(item.amount() - 1))
            } else {
                player.inventory.setItemStack(slot, net.minestom.server.item.ItemStack.AIR)
            }
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(5))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            if (player.gameMode == GameMode.SPECTATOR) return@forEach
            val chestItem = player.getEquipment(EquipmentSlot.CHESTPLATE)
            val hasElytra = chestItem.material() == Material.ELYTRA
            val isGliding = player.getTag(GLIDING_TAG)

            if (hasElytra && !player.isOnGround && player.velocity.y() < 0 && !isGliding) {
                player.setTag(GLIDING_TAG, true)
                player.isAllowFlying = true
                player.isFlying = true
            } else if (isGliding && (player.isOnGround || !hasElytra)) {
                player.setTag(GLIDING_TAG, false)
                if (player.gameMode != GameMode.CREATIVE) {
                    player.isAllowFlying = false
                    player.isFlying = false
                }
            }
        }
    }
}
