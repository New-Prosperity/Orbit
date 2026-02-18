package me.nebula.orbit.mechanic.cauldron

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material

class CauldronModule : OrbitModule("cauldron") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val block = event.block
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val held = event.player.getItemInMainHand()
            val slot = event.player.heldSlot.toInt()

            when (block.name()) {
                "minecraft:cauldron" -> handleEmptyCauldron(event.player, held, slot, instance, pos)
                "minecraft:water_cauldron" -> handleWaterCauldron(event.player, held, slot, instance, pos, block)
                "minecraft:lava_cauldron" -> handleLavaCauldron(event.player, held, slot, instance, pos)
                "minecraft:powder_snow_cauldron" -> handlePowderSnowCauldron(event.player, held, slot, instance, pos, block)
            }
        }
    }

    private fun handleEmptyCauldron(
        player: net.minestom.server.entity.Player,
        held: ItemStack,
        slot: Int,
        instance: net.minestom.server.instance.Instance,
        pos: net.minestom.server.coordinate.Point,
    ) {
        when (held.material()) {
            Material.WATER_BUCKET -> {
                instance.setBlock(pos, Block.WATER_CAULDRON.withProperty("level", "3"))
                player.inventory.setItemStack(slot, ItemStack.of(Material.BUCKET))
            }
            Material.LAVA_BUCKET -> {
                instance.setBlock(pos, Block.LAVA_CAULDRON)
                player.inventory.setItemStack(slot, ItemStack.of(Material.BUCKET))
            }
            Material.POWDER_SNOW_BUCKET -> {
                instance.setBlock(pos, Block.POWDER_SNOW_CAULDRON.withProperty("level", "3"))
                player.inventory.setItemStack(slot, ItemStack.of(Material.BUCKET))
            }
            else -> {}
        }
    }

    private fun handleWaterCauldron(
        player: net.minestom.server.entity.Player,
        held: ItemStack,
        slot: Int,
        instance: net.minestom.server.instance.Instance,
        pos: net.minestom.server.coordinate.Point,
        block: Block,
    ) {
        when (held.material()) {
            Material.BUCKET -> {
                val level = block.getProperty("level")?.toIntOrNull() ?: 0
                if (level >= 3) {
                    instance.setBlock(pos, Block.CAULDRON)
                    player.inventory.setItemStack(slot, ItemStack.of(Material.WATER_BUCKET))
                }
            }
            Material.GLASS_BOTTLE -> {
                val level = block.getProperty("level")?.toIntOrNull() ?: 0
                if (level > 0) {
                    val newLevel = level - 1
                    if (newLevel <= 0) {
                        instance.setBlock(pos, Block.CAULDRON)
                    } else {
                        instance.setBlock(pos, block.withProperty("level", newLevel.toString()))
                    }
                    if (held.amount() > 1) {
                        player.inventory.setItemStack(slot, held.withAmount(held.amount() - 1))
                    } else {
                        player.inventory.setItemStack(slot, ItemStack.AIR)
                    }
                    player.inventory.addItemStack(ItemStack.of(Material.POTION))
                }
            }
            else -> {}
        }
    }

    private fun handleLavaCauldron(
        player: net.minestom.server.entity.Player,
        held: ItemStack,
        slot: Int,
        instance: net.minestom.server.instance.Instance,
        pos: net.minestom.server.coordinate.Point,
    ) {
        if (held.material() == Material.BUCKET) {
            instance.setBlock(pos, Block.CAULDRON)
            player.inventory.setItemStack(slot, ItemStack.of(Material.LAVA_BUCKET))
        }
    }

    private fun handlePowderSnowCauldron(
        player: net.minestom.server.entity.Player,
        held: ItemStack,
        slot: Int,
        instance: net.minestom.server.instance.Instance,
        pos: net.minestom.server.coordinate.Point,
        block: Block,
    ) {
        if (held.material() == Material.BUCKET) {
            val level = block.getProperty("level")?.toIntOrNull() ?: 0
            if (level >= 3) {
                instance.setBlock(pos, Block.CAULDRON)
                player.inventory.setItemStack(slot, ItemStack.of(Material.POWDER_SNOW_BUCKET))
            }
        }
    }
}
