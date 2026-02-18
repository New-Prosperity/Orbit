package me.nebula.orbit.mechanic.drowning

import me.nebula.orbit.mechanic.food.addExhaustion
import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

private val AIR_SUPPLY_TAG = Tag.Integer("mechanic:drowning:air_supply").defaultValue(300)
private val DROWNING_TIMER_TAG = Tag.Integer("mechanic:drowning:timer").defaultValue(0)

class DrowningModule : OrbitModule("drowning") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            if (player.isDead || player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return@forEach
            processAir(player)
        }
    }

    private fun processAir(player: Player) {
        val instance = player.instance ?: return
        val headBlock = instance.getBlock(player.position.blockX(), (player.position.y() + player.eyeHeight).toInt(), player.position.blockZ())

        if (headBlock == Block.WATER) {
            val air = player.getTag(AIR_SUPPLY_TAG) - 1
            player.setTag(AIR_SUPPLY_TAG, air.coerceAtLeast(-20))

            if (air <= 0) {
                val timer = player.getTag(DROWNING_TIMER_TAG) + 1
                if (timer >= 2) {
                    player.damage(DamageType.DROWN, 2f)
                    player.addExhaustion(0.2f)
                    player.setTag(DROWNING_TIMER_TAG, 0)
                } else {
                    player.setTag(DROWNING_TIMER_TAG, timer)
                }
            }
        } else {
            player.setTag(AIR_SUPPLY_TAG, (player.getTag(AIR_SUPPLY_TAG) + 4).coerceAtMost(300))
            player.setTag(DROWNING_TIMER_TAG, 0)
        }
    }
}
