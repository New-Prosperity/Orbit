package me.nebula.orbit.mechanic.candle

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private val CANDLE_BLOCKS = setOf(
    "minecraft:candle",
    "minecraft:white_candle", "minecraft:orange_candle", "minecraft:magenta_candle",
    "minecraft:light_blue_candle", "minecraft:yellow_candle", "minecraft:lime_candle",
    "minecraft:pink_candle", "minecraft:gray_candle", "minecraft:light_gray_candle",
    "minecraft:cyan_candle", "minecraft:purple_candle", "minecraft:blue_candle",
    "minecraft:brown_candle", "minecraft:green_candle", "minecraft:red_candle",
    "minecraft:black_candle",
)

class CandleModule : OrbitModule("candle") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val blockName = block.name()
            if (blockName !in CANDLE_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val held = event.player.getItemInMainHand()

            val lit = block.getProperty("lit") == "true"

            if (lit && held.material() == Material.AIR) {
                instance.setBlock(pos, block.withProperty("lit", "false"))
                instance.playSound(
                    Sound.sound(SoundEvent.BLOCK_CANDLE_EXTINGUISH.key(), Sound.Source.BLOCK, 1f, 1f),
                    pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                )
                return@addListener
            }

            if (!lit && held.material() == Material.FLINT_AND_STEEL) {
                instance.setBlock(pos, block.withProperty("lit", "true"))
                instance.playSound(
                    Sound.sound(SoundEvent.ITEM_FLINTANDSTEEL_USE.key(), Sound.Source.BLOCK, 1f, 1f),
                    pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                )
                return@addListener
            }

            if (blockName == held.material().name() || held.material().name().endsWith("_candle")) {
                val candles = block.getProperty("candles")?.toIntOrNull() ?: 1
                if (candles < 4) {
                    instance.setBlock(pos, block.withProperty("candles", (candles + 1).toString()))
                }
            }
        }
    }
}
