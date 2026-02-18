package me.nebula.orbit.mechanic.beehive

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent

private val HIVE_BLOCKS = setOf("minecraft:beehive", "minecraft:bee_nest")

class BeehiveModule : OrbitModule("beehive") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            if (block.name() !in HIVE_BLOCKS) return@addListener

            val honeyLevel = block.getProperty("honey_level")?.toIntOrNull() ?: 0
            if (honeyLevel < 5) return@addListener

            val held = event.player.getItemInMainHand()
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            when (held.material()) {
                Material.GLASS_BOTTLE -> {
                    val slot = event.player.heldSlot.toInt()
                    if (held.amount() > 1) {
                        event.player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
                    } else {
                        event.player.inventory.setItemStack(slot, ItemStack.AIR)
                    }
                    event.player.inventory.addItemStack(ItemStack.of(Material.HONEY_BOTTLE))
                    instance.setBlock(pos, block.withProperty("honey_level", "0"))
                    instance.playSound(
                        Sound.sound(SoundEvent.ITEM_BOTTLE_FILL.key(), Sound.Source.BLOCK, 1f, 1f),
                        pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                    )
                }

                Material.SHEARS -> {
                    event.player.inventory.addItemStack(ItemStack.of(Material.HONEYCOMB, 3))
                    instance.setBlock(pos, block.withProperty("honey_level", "0"))
                    instance.playSound(
                        Sound.sound(SoundEvent.BLOCK_BEEHIVE_SHEAR.key(), Sound.Source.BLOCK, 1f, 1f),
                        pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5,
                    )
                }

                else -> return@addListener
            }
        }
    }
}
