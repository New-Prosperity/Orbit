package me.nebula.orbit.mechanic.dripstone

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.damage.Damage
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class DripstoneKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

class DripstoneModule : OrbitModule("dripstone") {

    private val pointedDripstones = ConcurrentHashMap.newKeySet<DripstoneKey>()

    override fun onEnable() {
        super.onEnable()

        pointedDripstones.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:pointed_dripstone") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            pointedDripstones.add(DripstoneKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val instance = player.instance ?: return@addListener
            val headBlock = instance.getBlock(player.position.add(0.0, player.eyeHeight, 0.0))

            if (headBlock.name() == "minecraft:pointed_dripstone") {
                val thickness = headBlock.getProperty("thickness") ?: "tip"
                if (thickness == "tip") {
                    player.damage(Damage(DamageType.STALAGMITE, null, null, null, 2f))
                }
            }
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = pointedDripstones.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.01f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                if (block.name() != "minecraft:pointed_dripstone") {
                    iterator.remove()
                    continue
                }

                val direction = block.getProperty("vertical_direction") ?: "down"
                if (direction == "down") {
                    val below = instance.getBlock(key.x, key.y - 1, key.z)
                    if (below == Block.AIR) {
                        instance.setBlock(key.x, key.y - 1, key.z,
                            Block.POINTED_DRIPSTONE
                                .withProperty("vertical_direction", "down")
                                .withProperty("thickness", "tip"))
                        pointedDripstones.add(DripstoneKey(key.instanceHash, key.x, key.y - 1, key.z))
                    }
                }
            }
        }.repeat(TaskSchedule.tick(200)).schedule()
    }

    override fun onDisable() {
        pointedDripstones.clear()
        super.onDisable()
    }
}
