package me.nebula.orbit.mechanic.redstonewire

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Point
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance

class RedstoneWireModule : OrbitModule("redstone-wire") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val block = event.block
            if (block.name() != "minecraft:redstone_wire") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            updateWireConnections(instance, pos)
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:redstone_wire") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            val neighbors = listOf(
                pos.add(1.0, 0.0, 0.0), pos.add(-1.0, 0.0, 0.0),
                pos.add(0.0, 0.0, 1.0), pos.add(0.0, 0.0, -1.0),
            )
            for (neighbor in neighbors) {
                val block = instance.getBlock(neighbor)
                if (block.name() == "minecraft:redstone_wire") {
                    updateWireConnections(instance, neighbor)
                }
            }
        }
    }

    private fun updateWireConnections(instance: Instance, pos: Point) {
        val block = instance.getBlock(pos)
        if (block.name() != "minecraft:redstone_wire") return

        var updated = block
        val directions = mapOf(
            "north" to pos.add(0.0, 0.0, -1.0),
            "south" to pos.add(0.0, 0.0, 1.0),
            "east" to pos.add(1.0, 0.0, 0.0),
            "west" to pos.add(-1.0, 0.0, 0.0),
        )

        for ((dir, neighborPos) in directions) {
            val neighbor = instance.getBlock(neighborPos)
            val connection = when {
                neighbor.name() == "minecraft:redstone_wire" -> "side"
                neighbor.isSolid -> {
                    val above = instance.getBlock(neighborPos.add(0.0, 1.0, 0.0))
                    if (above.name() == "minecraft:redstone_wire") "up" else "none"
                }
                else -> "none"
            }
            updated = updated.withProperty(dir, connection)
        }

        instance.setBlock(pos, updated)
    }
}
