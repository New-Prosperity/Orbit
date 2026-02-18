package me.nebula.orbit.mechanic.glasspane

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance

private val PANE_BLOCKS = buildSet {
    add("minecraft:glass_pane")
    add("minecraft:iron_bars")
    val colors = listOf(
        "white", "orange", "magenta", "light_blue", "yellow", "lime",
        "pink", "gray", "light_gray", "cyan", "purple", "blue",
        "brown", "green", "red", "black",
    )
    for (color in colors) add("minecraft:${color}_stained_glass_pane")
}

class GlassPaneModule : OrbitModule("glass-pane") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in PANE_BLOCKS) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            updateConnections(instance, pos.blockX(), pos.blockY(), pos.blockZ())
            updateNeighbors(instance, pos.blockX(), pos.blockY(), pos.blockZ())
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() !in PANE_BLOCKS) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            net.minestom.server.MinecraftServer.getSchedulerManager().buildTask {
                updateNeighbors(instance, pos.blockX(), pos.blockY(), pos.blockZ())
            }.delay(net.minestom.server.timer.TaskSchedule.tick(1)).schedule()
        }
    }

    private fun updateConnections(instance: Instance, x: Int, y: Int, z: Int) {
        val block = instance.getBlock(x, y, z)
        if (block.name() !in PANE_BLOCKS) return

        var updated = block
        updated = updated.withProperty("north", canConnect(instance, x, y, z - 1).toString())
        updated = updated.withProperty("south", canConnect(instance, x, y, z + 1).toString())
        updated = updated.withProperty("east", canConnect(instance, x + 1, y, z).toString())
        updated = updated.withProperty("west", canConnect(instance, x - 1, y, z).toString())
        instance.setBlock(x, y, z, updated)
    }

    private fun canConnect(instance: Instance, x: Int, y: Int, z: Int): Boolean {
        val block = instance.getBlock(x, y, z)
        return block.name() in PANE_BLOCKS || block.isSolid
    }

    private fun updateNeighbors(instance: Instance, x: Int, y: Int, z: Int) {
        updateConnections(instance, x - 1, y, z)
        updateConnections(instance, x + 1, y, z)
        updateConnections(instance, x, y, z - 1)
        updateConnections(instance, x, y, z + 1)
    }
}
