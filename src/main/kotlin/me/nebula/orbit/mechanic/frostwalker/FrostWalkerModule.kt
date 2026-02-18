package me.nebula.orbit.mechanic.frostwalker

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class FrostKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int, val expireAt: Long)

class FrostWalkerModule : OrbitModule("frost-walker") {

    private val frostedBlocks = ConcurrentHashMap.newKeySet<FrostKey>()

    override fun onEnable() {
        super.onEnable()
        frostedBlocks.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val boots = player.boots
            if (boots.material() == Material.AIR) return@addListener

            val instance = player.instance ?: return@addListener
            val pos = player.position
            val instanceHash = System.identityHashCode(instance)
            val now = System.currentTimeMillis()

            val radius = 2
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val bx = pos.blockX() + dx
                    val by = pos.blockY() - 1
                    val bz = pos.blockZ() + dz

                    val block = instance.getBlock(bx, by, bz)
                    if (block.name() == "minecraft:water") {
                        val above = instance.getBlock(bx, by + 1, bz)
                        if (above == Block.AIR) {
                            instance.setBlock(bx, by, bz, Block.FROSTED_ICE)
                            frostedBlocks.add(FrostKey(instanceHash, bx, by, bz, now + 10_000L))
                        }
                    }
                }
            }
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val now = System.currentTimeMillis()
            val iterator = frostedBlocks.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (now < key.expireAt) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() == "minecraft:frosted_ice") {
                    instance.setBlock(key.x, key.y, key.z, Block.WATER)
                }
                iterator.remove()
            }
        }.repeat(TaskSchedule.tick(20)).schedule()
    }

    override fun onDisable() {
        frostedBlocks.clear()
        super.onDisable()
    }
}
