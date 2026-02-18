package me.nebula.orbit.mechanic.dolphingrace

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

private const val PROXIMITY_RANGE = 5.0
private const val EFFECT_DURATION_TICKS = 100

class DolphinGraceModule : OrbitModule("dolphin-grace") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(40))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            if (player.isDead) return@forEach
            val instance = player.instance ?: return@forEach

            if (!isInWater(player, instance)) return@forEach

            val nearDolphin = instance.getNearbyEntities(player.position, PROXIMITY_RANGE)
                .any { it.entityType == EntityType.DOLPHIN && !it.isRemoved }

            if (nearDolphin) {
                player.addEffect(Potion(PotionEffect.DOLPHINS_GRACE, 0, EFFECT_DURATION_TICKS, Potion.ICON_FLAG))
            }
        }
    }

    private fun isInWater(player: Player, instance: Instance): Boolean {
        val block = instance.getBlock(player.position.blockX(), player.position.blockY(), player.position.blockZ())
        return block == Block.WATER
    }
}
