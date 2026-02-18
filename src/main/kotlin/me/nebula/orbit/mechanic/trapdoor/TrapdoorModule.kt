package me.nebula.orbit.mechanic.trapdoor

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.sound.SoundEvent

private val TRAPDOOR_BLOCKS = buildSet {
    val woods = listOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "bamboo", "crimson", "warped")
    for (wood in woods) add("minecraft:${wood}_trapdoor")
    add("minecraft:iron_trapdoor")
}

class TrapdoorModule : OrbitModule("trapdoor") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() !in TRAPDOOR_BLOCKS) return@addListener
            if (event.block.name() == "minecraft:iron_trapdoor") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val open = event.block.getProperty("open") ?: "false"
            val newState = if (open == "true") "false" else "true"

            instance.setBlock(pos, event.block.withProperty("open", newState))

            val sound = if (newState == "true") SoundEvent.BLOCK_WOODEN_TRAPDOOR_OPEN else SoundEvent.BLOCK_WOODEN_TRAPDOOR_CLOSE
            instance.playSound(
                Sound.sound(sound.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
