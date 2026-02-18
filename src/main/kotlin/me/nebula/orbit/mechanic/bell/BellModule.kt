package me.nebula.orbit.mechanic.bell

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.sound.SoundEvent

class BellModule : OrbitModule("bell") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:bell") return@addListener

            val pos = event.blockPosition
            event.player.instance?.playSound(
                Sound.sound(SoundEvent.BLOCK_BELL_USE.key(), Sound.Source.BLOCK, 2f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
