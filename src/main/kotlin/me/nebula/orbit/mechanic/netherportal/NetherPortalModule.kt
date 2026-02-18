package me.nebula.orbit.mechanic.netherportal

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Point
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material

private const val MIN_WIDTH = 4
private const val MAX_WIDTH = 23
private const val MIN_HEIGHT = 5
private const val MAX_HEIGHT = 23

class NetherPortalModule : OrbitModule("nether-portal") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.player.getItemInMainHand().material() != Material.FLINT_AND_STEEL) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val block = instance.getBlock(pos)

            if (block.name() != "minecraft:obsidian") return@addListener

            tryCreatePortal(instance, pos, "x") || tryCreatePortal(instance, pos, "z")
        }
    }

    private fun tryCreatePortal(instance: Instance, obsidianPos: Point, axis: String): Boolean {
        for (dy in 0 until MAX_HEIGHT) {
            val checkY = obsidianPos.add(0.0, dy.toDouble(), 0.0)
            if (instance.getBlock(checkY).name() != "minecraft:obsidian") continue

            val above = checkY.add(0.0, 1.0, 0.0)
            if (instance.getBlock(above) != Block.AIR) continue

            val result = scanFrame(instance, checkY, axis)
            if (result != null) {
                fillPortal(instance, result.first, result.second, axis)
                return true
            }
        }
        return false
    }

    private fun scanFrame(
        instance: Instance, bottomObsidian: Point, axis: String
    ): Pair<Point, Point>? {
        val dx = if (axis == "x") 1.0 else 0.0
        val dz = if (axis == "z") 1.0 else 0.0

        var width = 0
        for (i in 1 until MAX_WIDTH) {
            val check = bottomObsidian.add(dx * i, 0.0, dz * i)
            if (instance.getBlock(check).name() == "minecraft:obsidian") {
                width = i
                break
            }
            if (instance.getBlock(check) != Block.AIR && instance.getBlock(check.add(0.0, 1.0, 0.0)) != Block.AIR) return null
        }
        if (width < MIN_WIDTH - 2) return null

        var height = 0
        for (j in 1 until MAX_HEIGHT) {
            val check = bottomObsidian.add(0.0, j.toDouble(), 0.0)
            if (instance.getBlock(check).name() == "minecraft:obsidian") {
                height = j
                break
            }
        }
        if (height < MIN_HEIGHT - 2) return null

        val innerStart = bottomObsidian.add(dx, 1.0, dz)
        val innerEnd = bottomObsidian.add(dx * width, height.toDouble(), dz * width)

        for (h in 1 until height) {
            for (w in 1 until width) {
                val check = bottomObsidian.add(dx * w, h.toDouble(), dz * w)
                if (instance.getBlock(check) != Block.AIR) return null
            }
        }

        return innerStart to innerEnd
    }

    private fun fillPortal(instance: Instance, start: Point, end: Point, axis: String) {
        val dx = if (axis == "x") 1.0 else 0.0
        val dz = if (axis == "z") 1.0 else 0.0
        val portalBlock = Block.NETHER_PORTAL.withProperty("axis", axis)

        val minY = start.y().toInt()
        val maxY = end.y().toInt()
        val steps = if (axis == "x") {
            (start.x().toInt() until end.x().toInt())
        } else {
            (start.z().toInt() until end.z().toInt())
        }

        for (step in steps) {
            for (y in minY until maxY) {
                val pos = if (axis == "x") {
                    start.add((step - start.x().toInt()).toDouble(), (y - minY).toDouble(), 0.0)
                } else {
                    start.add(0.0, (y - minY).toDouble(), (step - start.z().toInt()).toDouble())
                }
                instance.setBlock(pos, portalBlock)
            }
        }
    }
}
