package me.nebula.orbit.mechanic.copperbulb

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.sound.SoundEvent

private val COPPER_BULB_BLOCKS = setOf(
    "minecraft:copper_bulb",
    "minecraft:exposed_copper_bulb",
    "minecraft:weathered_copper_bulb",
    "minecraft:oxidized_copper_bulb",
    "minecraft:waxed_copper_bulb",
    "minecraft:waxed_exposed_copper_bulb",
    "minecraft:waxed_weathered_copper_bulb",
    "minecraft:waxed_oxidized_copper_bulb",
)

class CopperBulbModule : OrbitModule("copper-bulb") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() !in COPPER_BULB_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val currentLit = block.getProperty("lit") ?: "false"
            val newLit = if (currentLit == "true") "false" else "true"
            val toggled = block.withProperty("lit", newLit).withProperty("powered", "true")

            instance.setBlock(pos, toggled)

            val soundEvent = if (newLit == "true") SoundEvent.BLOCK_COPPER_BULB_TURN_ON else SoundEvent.BLOCK_COPPER_BULB_TURN_OFF
            instance.playSound(
                Sound.sound(soundEvent.key(), Sound.Source.BLOCK, 1f, 1f),
                pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
            )
        }
    }
}
