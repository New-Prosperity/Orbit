package me.nebula.orbit.mechanic.sponge

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

private val WATER_BLOCKS = setOf(
    "minecraft:water", "minecraft:kelp", "minecraft:kelp_plant",
    "minecraft:seagrass", "minecraft:tall_seagrass", "minecraft:bubble_column",
)

class SpongeModule : OrbitModule("sponge") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:sponge") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val absorbed = absorbWater(instance, pos.blockX(), pos.blockY(), pos.blockZ())

            if (absorbed > 0) {
                val wetSponge = Block.fromKey("minecraft:wet_sponge") ?: return@addListener
                instance.setBlock(pos, wetSponge)
            }
        }
    }

    private fun absorbWater(instance: Instance, cx: Int, cy: Int, cz: Int): Int {
        var absorbed = 0
        val maxAbsorb = 65
        val range = 7

        for (dx in -range..range) {
            for (dy in -range..range) {
                for (dz in -range..range) {
                    if (absorbed >= maxAbsorb) return absorbed
                    if (kotlin.math.abs(dx) + kotlin.math.abs(dy) + kotlin.math.abs(dz) > range) continue

                    val x = cx + dx
                    val y = cy + dy
                    val z = cz + dz
                    val block = instance.getBlock(x, y, z)

                    if (block.name() in WATER_BLOCKS) {
                        instance.setBlock(x, y, z, Block.AIR)
                        absorbed++
                    } else {
                        val waterlogged = block.getProperty("waterlogged")
                        if (waterlogged == "true") {
                            instance.setBlock(x, y, z, block.withProperty("waterlogged", "false"))
                            absorbed++
                        }
                    }
                }
            }
        }
        return absorbed
    }
}
