package me.nebula.orbit.mechanic.sculkshrieker

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag

class SculkShriekerModule : OrbitModule("sculk-shrieker") {

    private val lastShriekTag = Tag.Long("mechanic:sculk_shrieker:last_shriek")

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener

            if (player.isSneaking) return@addListener

            val block = instance.getBlock(player.position)
            if (block.name() != "minecraft:sculk_shrieker") return@addListener

            val now = System.currentTimeMillis()
            val lastShriek = player.getTag(lastShriekTag) ?: 0L
            if (now - lastShriek < 10000L) return@addListener

            player.setTag(lastShriekTag, now)

            val pos = player.position
            instance.playSound(
                Sound.sound(SoundEvent.BLOCK_SCULK_SHRIEKER_SHRIEK.key(), Sound.Source.BLOCK, 2f, 1f),
                pos.x(), pos.y(), pos.z(),
            )

            val shrieking = block.withProperty("can_summon", "false").withProperty("shrieking", "true")
            instance.setBlock(pos, shrieking)

            net.minestom.server.MinecraftServer.getSchedulerManager().buildTask {
                val current = instance.getBlock(pos)
                if (current.name() == "minecraft:sculk_shrieker") {
                    instance.setBlock(pos, current.withProperty("shrieking", "false"))
                }
            }.delay(net.minestom.server.timer.TaskSchedule.tick(90)).schedule()
        }
    }
}
