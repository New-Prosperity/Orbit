package me.nebula.orbit.mechanic.rail

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance

private val RAIL_BLOCKS = setOf(
    "minecraft:rail", "minecraft:powered_rail",
    "minecraft:detector_rail", "minecraft:activator_rail",
)

class RailModule : OrbitModule("rail") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in RAIL_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val shape = determineRailShape(instance, pos.blockX(), pos.blockY(), pos.blockZ(), event.block.name())
            if (shape != null) {
                instance.setBlock(pos, event.block.withProperty("shape", shape))
                event.isCancelled = true
                instance.setBlock(pos, event.block.withProperty("shape", shape))
            }
        }
    }

    private fun determineRailShape(instance: Instance, x: Int, y: Int, z: Int, blockName: String): String? {
        val north = hasRail(instance, x, y, z - 1) || hasRail(instance, x, y + 1, z - 1)
        val south = hasRail(instance, x, y, z + 1) || hasRail(instance, x, y + 1, z + 1)
        val east = hasRail(instance, x + 1, y, z) || hasRail(instance, x + 1, y + 1, z)
        val west = hasRail(instance, x - 1, y, z) || hasRail(instance, x - 1, y + 1, z)

        val isNormal = blockName == "minecraft:rail"

        return when {
            north && south && east && isNormal -> "north_south"
            east && west -> "east_west"
            north && south -> "north_south"
            north && east && isNormal -> "north_east"
            north && west && isNormal -> "north_west"
            south && east && isNormal -> "south_east"
            south && west && isNormal -> "south_west"
            north || south -> "north_south"
            else -> "east_west"
        }
    }

    private fun hasRail(instance: Instance, x: Int, y: Int, z: Int): Boolean =
        instance.getBlock(x, y, z).name() in RAIL_BLOCKS
}
