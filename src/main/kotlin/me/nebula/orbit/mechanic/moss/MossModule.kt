package me.nebula.orbit.mechanic.moss

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

private val CONVERTIBLE_TO_MOSS = setOf(
    "minecraft:stone", "minecraft:dirt", "minecraft:coarse_dirt",
    "minecraft:granite", "minecraft:diorite", "minecraft:andesite",
    "minecraft:tuff", "minecraft:deepslate",
)

class MossModule : OrbitModule("moss") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:moss_block") return@addListener
            if (event.player.getItemInMainHand().material() != Material.BONE_MEAL) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val range = 3

            val mossCarpet = Block.fromKey("minecraft:moss_carpet")
            val mossBlock = Block.fromKey("minecraft:moss_block") ?: return@addListener

            for (dx in -range..range) {
                for (dz in -range..range) {
                    val bx = pos.blockX() + dx
                    val bz = pos.blockZ() + dz
                    val by = pos.blockY()

                    val block = instance.getBlock(bx, by, bz)
                    if (block.name() in CONVERTIBLE_TO_MOSS) {
                        instance.setBlock(bx, by, bz, mossBlock)
                        if (mossCarpet != null && instance.getBlock(bx, by + 1, bz).isAir) {
                            if (kotlin.random.Random.nextFloat() < 0.3f) {
                                instance.setBlock(bx, by + 1, bz, mossCarpet)
                            }
                        }
                    }
                }
            }

            val consumed = event.player.getItemInMainHand().consume(1)
            event.player.setItemInMainHand(consumed)
        }
    }
}
