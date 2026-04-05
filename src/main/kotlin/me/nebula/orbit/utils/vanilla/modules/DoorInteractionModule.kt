package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.sound.SoundEvent

private val DOOR_NAMES = setOf(
    "minecraft:oak_door", "minecraft:spruce_door", "minecraft:birch_door",
    "minecraft:jungle_door", "minecraft:acacia_door", "minecraft:dark_oak_door",
    "minecraft:mangrove_door", "minecraft:cherry_door", "minecraft:bamboo_door",
    "minecraft:crimson_door", "minecraft:warped_door", "minecraft:iron_door",
    "minecraft:copper_door", "minecraft:exposed_copper_door",
    "minecraft:weathered_copper_door", "minecraft:oxidized_copper_door",
)

private val TRAPDOOR_NAMES = setOf(
    "minecraft:oak_trapdoor", "minecraft:spruce_trapdoor", "minecraft:birch_trapdoor",
    "minecraft:jungle_trapdoor", "minecraft:acacia_trapdoor", "minecraft:dark_oak_trapdoor",
    "minecraft:mangrove_trapdoor", "minecraft:cherry_trapdoor", "minecraft:bamboo_trapdoor",
    "minecraft:crimson_trapdoor", "minecraft:warped_trapdoor", "minecraft:iron_trapdoor",
    "minecraft:copper_trapdoor", "minecraft:exposed_copper_trapdoor",
    "minecraft:weathered_copper_trapdoor", "minecraft:oxidized_copper_trapdoor",
)

private val FENCE_GATE_NAMES = setOf(
    "minecraft:oak_fence_gate", "minecraft:spruce_fence_gate", "minecraft:birch_fence_gate",
    "minecraft:jungle_fence_gate", "minecraft:acacia_fence_gate", "minecraft:dark_oak_fence_gate",
    "minecraft:mangrove_fence_gate", "minecraft:cherry_fence_gate", "minecraft:bamboo_fence_gate",
    "minecraft:crimson_fence_gate", "minecraft:warped_fence_gate",
)

private val IRON_DOORS = setOf("minecraft:iron_door", "minecraft:iron_trapdoor")

object DoorInteractionModule : VanillaModule {

    override val id = "door-interaction"
    override val description = "Open and close doors, trapdoors, and fence gates on right-click"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-door-interaction")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val blockName = event.block.name()

            when {
                blockName in DOOR_NAMES -> {
                    if (blockName in IRON_DOORS) return@addListener
                    val open = event.block.getProperty("open") ?: "false"
                    val newOpen = if (open == "true") "false" else "true"
                    event.instance.setBlock(event.blockPosition, event.block.withProperty("open", newOpen))

                    val otherHalf = event.block.getProperty("half")
                    val otherY = if (otherHalf == "lower") event.blockPosition.blockY() + 1 else event.blockPosition.blockY() - 1
                    val otherBlock = event.instance.getBlock(event.blockPosition.blockX(), otherY, event.blockPosition.blockZ())
                    if (otherBlock.name() == blockName) {
                        event.instance.setBlock(event.blockPosition.blockX(), otherY, event.blockPosition.blockZ(), otherBlock.withProperty("open", newOpen))
                    }

                    val sound = if (newOpen == "true") SoundEvent.BLOCK_WOODEN_DOOR_OPEN else SoundEvent.BLOCK_WOODEN_DOOR_CLOSE
                    event.player.playSound(sound)
                }
                blockName in TRAPDOOR_NAMES -> {
                    if (blockName in IRON_DOORS) return@addListener
                    val open = event.block.getProperty("open") ?: "false"
                    val newOpen = if (open == "true") "false" else "true"
                    event.instance.setBlock(event.blockPosition, event.block.withProperty("open", newOpen))

                    val sound = if (newOpen == "true") SoundEvent.BLOCK_WOODEN_TRAPDOOR_OPEN else SoundEvent.BLOCK_WOODEN_TRAPDOOR_CLOSE
                    event.player.playSound(sound)
                }
                blockName in FENCE_GATE_NAMES -> {
                    val open = event.block.getProperty("open") ?: "false"
                    val newOpen = if (open == "true") "false" else "true"
                    event.instance.setBlock(event.blockPosition, event.block.withProperty("open", newOpen))

                    val sound = if (newOpen == "true") SoundEvent.BLOCK_FENCE_GATE_OPEN else SoundEvent.BLOCK_FENCE_GATE_CLOSE
                    event.player.playSound(sound)
                }
            }
        }

        return node
    }
}
