package me.nebula.orbit.mechanic.fence

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

private val FENCE_BLOCKS = buildSet {
    val woods = listOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "mangrove", "bamboo", "crimson", "warped")
    woods.forEach { add("minecraft:${it}_fence") }
    add("minecraft:nether_brick_fence")
}

private val FENCE_GATE_BLOCKS = buildSet {
    val woods = listOf("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "cherry", "mangrove", "bamboo", "crimson", "warped")
    woods.forEach { add("minecraft:${it}_fence_gate") }
}

private val WALL_BLOCKS = buildSet {
    val types = listOf(
        "cobblestone", "mossy_cobblestone", "stone_brick", "mossy_stone_brick",
        "granite", "diorite", "andesite", "brick", "prismarine",
        "red_sandstone", "sandstone", "nether_brick", "red_nether_brick",
        "end_stone_brick", "blackstone", "polished_blackstone", "polished_blackstone_brick",
        "cobbled_deepslate", "polished_deepslate", "deepslate_brick", "deepslate_tile", "mud_brick",
    )
    types.forEach { add("minecraft:${it}_wall") }
}

class FenceModule : OrbitModule("fence") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val block = event.block
            val blockName = block.name()

            when {
                blockName in FENCE_BLOCKS -> updateFenceConnections(event)
                blockName in WALL_BLOCKS -> updateWallConnections(event)
            }
        }
    }

    private fun updateFenceConnections(event: PlayerBlockPlaceEvent) {
        val instance = event.player.instance ?: return
        val pos = event.blockPosition
        val block = event.block

        val directions = mapOf(
            "north" to pos.add(0.0, 0.0, -1.0),
            "south" to pos.add(0.0, 0.0, 1.0),
            "east" to pos.add(1.0, 0.0, 0.0),
            "west" to pos.add(-1.0, 0.0, 0.0),
        )

        var updated = block
        for ((dir, neighborPos) in directions) {
            val neighbor = instance.getBlock(neighborPos).name()
            val connects = neighbor in FENCE_BLOCKS || neighbor in FENCE_GATE_BLOCKS || instance.getBlock(neighborPos).isSolid
            updated = updated.withProperty(dir, connects.toString())
        }

        instance.setBlock(pos, updated)
        updateNeighborFences(instance, pos)
    }

    private fun updateWallConnections(event: PlayerBlockPlaceEvent) {
        val instance = event.player.instance ?: return
        val pos = event.blockPosition
        val block = event.block

        val directions = mapOf(
            "north" to pos.add(0.0, 0.0, -1.0),
            "south" to pos.add(0.0, 0.0, 1.0),
            "east" to pos.add(1.0, 0.0, 0.0),
            "west" to pos.add(-1.0, 0.0, 0.0),
        )

        var updated = block
        for ((dir, neighborPos) in directions) {
            val neighbor = instance.getBlock(neighborPos).name()
            val connection = when {
                neighbor in WALL_BLOCKS -> "low"
                instance.getBlock(neighborPos).isSolid -> "tall"
                else -> "none"
            }
            updated = updated.withProperty(dir, connection)
        }

        val aboveBlock = instance.getBlock(pos.add(0.0, 1.0, 0.0))
        updated = updated.withProperty("up", aboveBlock.isSolid.toString())

        instance.setBlock(pos, updated)
    }

    private fun updateNeighborFences(instance: Instance, pos: net.minestom.server.coordinate.Point) {
        val neighbors = listOf(
            pos.add(0.0, 0.0, -1.0), pos.add(0.0, 0.0, 1.0),
            pos.add(1.0, 0.0, 0.0), pos.add(-1.0, 0.0, 0.0),
        )
        for (neighborPos in neighbors) {
            val neighbor = instance.getBlock(neighborPos)
            if (neighbor.name() !in FENCE_BLOCKS) continue

            val dirs = mapOf(
                "north" to neighborPos.add(0.0, 0.0, -1.0),
                "south" to neighborPos.add(0.0, 0.0, 1.0),
                "east" to neighborPos.add(1.0, 0.0, 0.0),
                "west" to neighborPos.add(-1.0, 0.0, 0.0),
            )
            var updated = neighbor
            for ((dir, adjPos) in dirs) {
                val adj = instance.getBlock(adjPos).name()
                val connects = adj in FENCE_BLOCKS || adj in FENCE_GATE_BLOCKS || instance.getBlock(adjPos).isSolid
                updated = updated.withProperty(dir, connects.toString())
            }
            instance.setBlock(neighborPos, updated)
        }
    }
}
