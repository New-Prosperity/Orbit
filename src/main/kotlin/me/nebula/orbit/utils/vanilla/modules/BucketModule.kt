package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import net.minestom.server.entity.GameMode
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

object BucketModule : VanillaModule {

    override val id = "buckets"
    override val description = "Place and collect water/lava with buckets"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val node = EventNode.all("vanilla-buckets")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val item = event.player.itemInMainHand
            val material = item.material()

            when (material) {
                Material.WATER_BUCKET -> {
                    val targetPos = event.blockPosition.relative(event.blockFace)
                    val targetBlock = event.instance.getBlock(targetPos)
                    if (targetBlock.isAir || targetBlock.compare(Block.WATER)) {
                        event.instance.setBlock(targetPos, Block.WATER)
                        event.player.playSound(SoundEvent.ITEM_BUCKET_EMPTY)
                        if (event.player.gameMode != GameMode.CREATIVE) {
                            event.player.setItemInMainHand(ItemStack.of(Material.BUCKET))
                        }
                    }
                }
                Material.LAVA_BUCKET -> {
                    val targetPos = event.blockPosition.relative(event.blockFace)
                    val targetBlock = event.instance.getBlock(targetPos)
                    if (targetBlock.isAir || targetBlock.compare(Block.LAVA)) {
                        event.instance.setBlock(targetPos, Block.LAVA)
                        event.player.playSound(SoundEvent.ITEM_BUCKET_EMPTY_LAVA)
                        if (event.player.gameMode != GameMode.CREATIVE) {
                            event.player.setItemInMainHand(ItemStack.of(Material.BUCKET))
                        }
                    }
                }
                Material.BUCKET -> {
                    val clickedBlock = event.block
                    when {
                        clickedBlock.compare(Block.WATER) -> {
                            event.instance.setBlock(event.blockPosition, Block.AIR)
                            event.player.playSound(SoundEvent.ITEM_BUCKET_FILL)
                            if (event.player.gameMode != GameMode.CREATIVE) {
                                event.player.setItemInMainHand(ItemStack.of(Material.WATER_BUCKET))
                            }
                        }
                        clickedBlock.compare(Block.LAVA) -> {
                            event.instance.setBlock(event.blockPosition, Block.AIR)
                            event.player.playSound(SoundEvent.ITEM_BUCKET_FILL_LAVA)
                            if (event.player.gameMode != GameMode.CREATIVE) {
                                event.player.setItemInMainHand(ItemStack.of(Material.LAVA_BUCKET))
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        return node
    }
}
