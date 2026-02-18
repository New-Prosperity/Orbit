package me.nebula.orbit.mechanic.powdersnowfreezing

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

private val FREEZE_TICKS_TAG = Tag.Integer("mechanic:powder_snow_freezing:ticks").defaultValue(0)

private const val FREEZE_THRESHOLD_TICKS = 140
private const val DAMAGE_INTERVAL_TICKS = 20

class PowderSnowFreezingModule : OrbitModule("powder-snow-freezing") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(DAMAGE_INTERVAL_TICKS))
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
            processFreeze(player)
        }
    }

    private fun processFreeze(player: Player) {
        val instance = player.instance ?: return
        val block = instance.getBlock(player.position)

        if (block.name() != "minecraft:powder_snow") {
            val ticks = player.getTag(FREEZE_TICKS_TAG)
            if (ticks > 0) player.setTag(FREEZE_TICKS_TAG, (ticks - 2).coerceAtLeast(0))
            return
        }

        if (player.boots.material() == Material.LEATHER_BOOTS) return

        val ticks = player.getTag(FREEZE_TICKS_TAG) + DAMAGE_INTERVAL_TICKS
        player.setTag(FREEZE_TICKS_TAG, ticks)

        if (ticks >= FREEZE_THRESHOLD_TICKS) {
            player.damage(DamageType.FREEZE, 1f)
            player.setTag(FREEZE_TICKS_TAG, FREEZE_THRESHOLD_TICKS - DAMAGE_INTERVAL_TICKS)
        }
    }
}
