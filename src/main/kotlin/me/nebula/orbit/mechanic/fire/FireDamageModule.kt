package me.nebula.orbit.mechanic.fire

import me.nebula.orbit.mechanic.food.addExhaustion
import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

class FireDamageModule : OrbitModule("fire") {

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
            checkFireContact(player)
            checkLavaContact(player)
        }
    }

    private fun checkFireContact(player: Player) {
        val instance = player.instance ?: return
        val pos = player.position
        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block == Block.FIRE || block == Block.SOUL_FIRE) {
            player.entityMeta.setOnFire(true)
            player.damage(DamageType.IN_FIRE, 1f)
            player.addExhaustion(0.1f)
        }
    }

    private fun checkLavaContact(player: Player) {
        val instance = player.instance ?: return
        val pos = player.position
        val block = instance.getBlock(pos.blockX(), pos.blockY(), pos.blockZ())
        if (block == Block.LAVA) {
            player.entityMeta.setOnFire(true)
            player.damage(DamageType.LAVA, 4f)
            player.addExhaustion(0.1f)
        }
    }
}
