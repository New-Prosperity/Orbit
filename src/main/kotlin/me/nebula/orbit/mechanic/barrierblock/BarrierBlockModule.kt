package me.nebula.orbit.mechanic.barrierblock

import me.nebula.orbit.module.OrbitModule
import me.nebula.orbit.utils.blockindex.BlockPositionIndex
import me.nebula.orbit.utils.particle.spawnParticle
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.item.Material
import net.minestom.server.particle.Particle
import net.minestom.server.tag.Tag

private val BARRIER_VISIBLE_TAG = Tag.Boolean("mechanic:barrier:visible").defaultValue(false)

class BarrierBlockModule : OrbitModule("barrier-block") {

    private val index = BlockPositionIndex(setOf("minecraft:barrier"), eventNode).install()

    override fun onEnable() {
        super.onEnable()

        index.instancePositions.cleanOnInstanceRemove { it }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:barrier") return@addListener
            if (event.player.gameMode != GameMode.CREATIVE) {
                event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() != "minecraft:barrier") return@addListener
            if (event.player.gameMode != GameMode.CREATIVE) {
                event.isCancelled = true
            }
        }

        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            val player = event.player
            if (player.gameMode != GameMode.CREATIVE) return@addListener

            val holdingBarrier = player.itemInMainHand.material() == Material.BARRIER
                || player.itemInOffHand.material() == Material.BARRIER

            player.setTag(BARRIER_VISIBLE_TAG, holdingBarrier)

            if (holdingBarrier) {
                val instance = player.instance ?: return@addListener
                index.positionsNear(instance, player.position.asVec(), 8.0).forEach { vec ->
                    player.spawnParticle(
                        Particle.BLOCK_MARKER,
                        Pos(vec.x() + 0.5, vec.y() + 0.5, vec.z() + 0.5),
                    )
                }
            }
        }
    }

    override fun onDisable() {
        index.clear()
        super.onDisable()
    }
}
