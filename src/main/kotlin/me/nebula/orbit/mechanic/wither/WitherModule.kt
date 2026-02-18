package me.nebula.orbit.mechanic.wither

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block

private val WITHER_SKULL_BLOCKS = setOf("minecraft:wither_skeleton_skull", "minecraft:wither_skeleton_wall_skull")

class WitherModule : OrbitModule("wither") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in WITHER_SKULL_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            checkWitherPattern(instance, pos)
        }
    }

    private fun checkWitherPattern(instance: Instance, skullPos: net.minestom.server.coordinate.Point) {
        for (dx in -2..0) {
            if (checkPatternAt(instance, skullPos, dx, 0)) return
        }
        for (dz in -2..0) {
            if (checkPatternAt(instance, skullPos, 0, dz)) return
        }
    }

    private fun checkPatternAt(instance: Instance, skullPos: net.minestom.server.coordinate.Point, offsetX: Int, offsetZ: Int): Boolean {
        val isXAxis = offsetZ == 0
        val positions = if (isXAxis) {
            (0..2).map { Vec(skullPos.x() + offsetX + it.toDouble(), skullPos.y().toDouble(), skullPos.z().toDouble()) }
        } else {
            (0..2).map { Vec(skullPos.x().toDouble(), skullPos.y().toDouble(), skullPos.z() + offsetZ + it.toDouble()) }
        }

        val allSkulls = positions.all { instance.getBlock(it).name() in WITHER_SKULL_BLOCKS }
        if (!allSkulls) return false

        val bodyPositions = positions.map { Vec(it.x(), it.y() - 1.0, it.z()) }
        val allSoulSand = bodyPositions.all { instance.getBlock(it).name() == "minecraft:soul_sand" }
        if (!allSoulSand) return false

        val centerBody = bodyPositions[1]
        val belowCenter = Vec(centerBody.x(), centerBody.y() - 1.0, centerBody.z())
        if (instance.getBlock(belowCenter).name() != "minecraft:soul_sand") return false

        for (pos in positions) instance.setBlock(pos, Block.AIR)
        for (pos in bodyPositions) instance.setBlock(pos, Block.AIR)
        instance.setBlock(belowCenter, Block.AIR)

        return true
    }
}
