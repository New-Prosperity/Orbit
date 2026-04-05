package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent
import java.time.Duration

private val BUTTON_NAMES = setOf(
    "minecraft:oak_button", "minecraft:spruce_button", "minecraft:birch_button",
    "minecraft:jungle_button", "minecraft:acacia_button", "minecraft:dark_oak_button",
    "minecraft:mangrove_button", "minecraft:cherry_button", "minecraft:bamboo_button",
    "minecraft:crimson_button", "minecraft:warped_button",
    "minecraft:stone_button", "minecraft:polished_blackstone_button",
)

private val WOODEN_BUTTONS = setOf(
    "minecraft:oak_button", "minecraft:spruce_button", "minecraft:birch_button",
    "minecraft:jungle_button", "minecraft:acacia_button", "minecraft:dark_oak_button",
    "minecraft:mangrove_button", "minecraft:cherry_button", "minecraft:bamboo_button",
    "minecraft:crimson_button", "minecraft:warped_button",
)

object LeverButtonModule : VanillaModule {

    override val id = "lever-button"
    override val description = "Levers toggle on/off, buttons activate temporarily"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-lever-button")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val blockName = event.block.name()

            if (blockName == "minecraft:lever") {
                val powered = event.block.getProperty("powered") ?: "false"
                val newPowered = if (powered == "true") "false" else "true"
                event.instance.setBlock(event.blockPosition, event.block.withProperty("powered", newPowered))
                event.player.playSound(SoundEvent.BLOCK_LEVER_CLICK)
            }

            if (blockName in BUTTON_NAMES) {
                val powered = event.block.getProperty("powered") ?: "false"
                if (powered == "true") return@addListener

                event.instance.setBlock(event.blockPosition, event.block.withProperty("powered", "true"))
                event.player.playSound(SoundEvent.BLOCK_STONE_BUTTON_CLICK_ON)

                val resetDelay = if (blockName in WOODEN_BUTTONS) 30L else 20L
                val pos = event.blockPosition
                val originalBlock = event.block
                event.instance.scheduler().buildTask {
                    val current = event.instance.getBlock(pos)
                    if (current.name() == blockName) {
                        event.instance.setBlock(pos, current.withProperty("powered", "false"))
                    }
                }.delay(Duration.ofMillis(resetDelay * 50)).schedule()
            }
        }

        return node
    }
}
