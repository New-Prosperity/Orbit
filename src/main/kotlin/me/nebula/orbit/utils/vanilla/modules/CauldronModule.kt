package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

object CauldronModule : VanillaModule {

    override val id = "cauldron"
    override val description = "Cauldron water storage: fill/empty with buckets and bottles, 3 water levels"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-cauldron")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val blockName = event.block.name()
            val item = event.player.itemInMainHand
            val material = item.material()

            when (blockName) {
                "minecraft:cauldron" -> {
                    when (material) {
                        Material.WATER_BUCKET -> {
                            event.instance.setBlock(event.blockPosition, Block.WATER_CAULDRON.withProperty("level", "3"))
                            if (event.player.gameMode != GameMode.CREATIVE) {
                                event.player.setItemInMainHand(ItemStack.of(Material.BUCKET))
                            }
                            event.player.playSound(SoundEvent.ITEM_BUCKET_EMPTY)
                        }
                        Material.LAVA_BUCKET -> {
                            event.instance.setBlock(event.blockPosition, Block.LAVA_CAULDRON)
                            if (event.player.gameMode != GameMode.CREATIVE) {
                                event.player.setItemInMainHand(ItemStack.of(Material.BUCKET))
                            }
                            event.player.playSound(SoundEvent.ITEM_BUCKET_EMPTY_LAVA)
                        }
                        Material.GLASS_BOTTLE -> {
                        }
                        else -> {}
                    }
                }
                "minecraft:water_cauldron" -> {
                    val level = event.block.getProperty("level")?.toIntOrNull() ?: 0
                    when (material) {
                        Material.BUCKET -> {
                            if (level >= 3) {
                                event.instance.setBlock(event.blockPosition, Block.CAULDRON)
                                if (event.player.gameMode != GameMode.CREATIVE) {
                                    event.player.setItemInMainHand(ItemStack.of(Material.WATER_BUCKET))
                                }
                                event.player.playSound(SoundEvent.ITEM_BUCKET_FILL)
                            }
                        }
                        Material.GLASS_BOTTLE -> {
                            if (level > 0) {
                                val newLevel = level - 1
                                if (newLevel <= 0) {
                                    event.instance.setBlock(event.blockPosition, Block.CAULDRON)
                                } else {
                                    event.instance.setBlock(event.blockPosition, event.block.withProperty("level", newLevel.toString()))
                                }
                                if (event.player.gameMode != GameMode.CREATIVE) {
                                    event.player.setItemInMainHand(item.consume(1))
                                    event.player.inventory.addItemStack(ItemStack.of(Material.POTION))
                                }
                            }
                        }
                        Material.WATER_BUCKET -> {
                            if (level < 3) {
                                event.instance.setBlock(event.blockPosition, event.block.withProperty("level", "3"))
                                if (event.player.gameMode != GameMode.CREATIVE) {
                                    event.player.setItemInMainHand(ItemStack.of(Material.BUCKET))
                                }
                                event.player.playSound(SoundEvent.ITEM_BUCKET_EMPTY)
                            }
                        }
                        else -> {}
                    }
                }
                "minecraft:lava_cauldron" -> {
                    if (material == Material.BUCKET) {
                        event.instance.setBlock(event.blockPosition, Block.CAULDRON)
                        if (event.player.gameMode != GameMode.CREATIVE) {
                            event.player.setItemInMainHand(ItemStack.of(Material.LAVA_BUCKET))
                        }
                        event.player.playSound(SoundEvent.ITEM_BUCKET_FILL_LAVA)
                    }
                }
            }
        }

        return node
    }
}
