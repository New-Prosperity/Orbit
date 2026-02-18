package me.nebula.orbit.mechanic.sweetberrybush

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule

private val LAST_BUSH_DAMAGE_TAG = Tag.Long("mechanic:sweet_berry_bush:last_damage").defaultValue(0L)
private val IN_BUSH_TAG = Tag.Boolean("mechanic:sweet_berry_bush:inside").defaultValue(false)

class SweetBerryBushModule : OrbitModule("sweet-berry-bush") {

    private var tickTask: Task? = null

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val block = instance.getBlock(player.position)

            if (block.name() != "minecraft:sweet_berry_bush") {
                player.setTag(IN_BUSH_TAG, false)
                return@addListener
            }

            player.setTag(IN_BUSH_TAG, true)

            val velocity = player.velocity
            player.velocity = net.minestom.server.coordinate.Vec(
                velocity.x() * 0.2,
                velocity.y(),
                velocity.z() * 0.2,
            )
        }

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
            if (!player.getTag(IN_BUSH_TAG)) return@forEach

            val now = System.currentTimeMillis()
            val lastDamage = player.getTag(LAST_BUSH_DAMAGE_TAG)
            if (now - lastDamage < 1000L) return@forEach

            player.damage(DamageType.SWEET_BERRY_BUSH, 1f)
            player.setTag(LAST_BUSH_DAMAGE_TAG, now)
        }
    }
}
