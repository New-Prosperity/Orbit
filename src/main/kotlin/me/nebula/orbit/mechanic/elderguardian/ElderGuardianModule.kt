package me.nebula.orbit.mechanic.elderguardian

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private const val EFFECT_RANGE = 50.0
private const val FATIGUE_DURATION_TICKS = 6000
private const val FATIGUE_AMPLIFIER = 2
private const val SCAN_INTERVAL_TICKS = 60 * 20

class ElderGuardianModule : OrbitModule("elder-guardian") {

    private var tickTask: Task? = null
    private val affectedPlayers: MutableSet<Player> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        affectedPlayers.clear()
        super.onDisable()
    }

    private fun tick() {
        affectedPlayers.removeIf { it.isRemoved || !it.isOnline }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            if (player.isDead) return@forEach
            val instance = player.instance ?: return@forEach

            val nearElderGuardian = instance.entities.any { entity ->
                entity.entityType == EntityType.ELDER_GUARDIAN &&
                    !entity.isRemoved &&
                    entity.position.distanceSquared(player.position) <= EFFECT_RANGE * EFFECT_RANGE
            }

            if (nearElderGuardian) {
                player.addEffect(Potion(PotionEffect.MINING_FATIGUE, FATIGUE_AMPLIFIER, FATIGUE_DURATION_TICKS))
                affectedPlayers.add(player)
            }
        }
    }
}
