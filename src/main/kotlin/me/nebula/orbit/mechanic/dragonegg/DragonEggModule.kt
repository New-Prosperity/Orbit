package me.nebula.orbit.mechanic.dragonegg

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import kotlin.random.Random

class DragonEggModule : OrbitModule("dragon-egg") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockInteractEvent::class.java) { event ->
            if (event.block.name() != "minecraft:dragon_egg") return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition

            repeat(7) {
                val dx = Random.nextInt(-15, 16)
                val dy = Random.nextInt(-15, 16)
                val dz = Random.nextInt(-15, 16)
                val target = pos.add(dx.toDouble(), dy.toDouble(), dz.toDouble())

                if (isValidTeleportTarget(instance, target)) {
                    instance.setBlock(pos, Block.AIR)
                    instance.setBlock(target, Block.DRAGON_EGG)
                    return@addListener
                }
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:dragon_egg") return@addListener
            event.isCancelled = true
        }
    }

    private fun isValidTeleportTarget(instance: Instance, pos: net.minestom.server.coordinate.Point): Boolean {
        val block = instance.getBlock(pos)
        if (block != Block.AIR) return false
        val below = instance.getBlock(pos.add(0.0, -1.0, 0.0))
        return below.isSolid
    }
}
