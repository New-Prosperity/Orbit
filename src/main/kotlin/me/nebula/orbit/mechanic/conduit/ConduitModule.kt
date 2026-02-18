package me.nebula.orbit.mechanic.conduit

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private data class ConduitKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val PRISMARINE_BLOCKS = setOf(
    "minecraft:prismarine", "minecraft:prismarine_bricks", "minecraft:dark_prismarine",
    "minecraft:sea_lantern",
)

class ConduitModule : OrbitModule("conduit") {

    private val activeConduits = ConcurrentHashMap.newKeySet<ConduitKey>()

    override fun onEnable() {
        super.onEnable()
        activeConduits.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() != "minecraft:conduit") return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            val key = ConduitKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ())

            if (checkFrame(instance, pos)) {
                activeConduits.add(key)
            }
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = activeConduits.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }
                val pos = net.minestom.server.coordinate.Vec(key.x.toDouble(), key.y.toDouble(), key.z.toDouble())
                val block = instance.getBlock(pos)
                if (block.name() != "minecraft:conduit") {
                    iterator.remove()
                    continue
                }

                val range = 32.0
                instance.players
                    .filter { it.position.distance(pos) <= range }
                    .forEach { applyConduitPower(it) }
            }
        }.repeat(TaskSchedule.tick(80)).schedule()
    }

    override fun onDisable() {
        activeConduits.clear()
        super.onDisable()
    }

    private fun checkFrame(instance: Instance, pos: Point): Boolean {
        var count = 0
        for (dx in -2..2) {
            for (dy in -2..2) {
                for (dz in -2..2) {
                    if (dx == 0 && dy == 0 && dz == 0) continue
                    val checkPos = pos.add(dx.toDouble(), dy.toDouble(), dz.toDouble())
                    if (instance.getBlock(checkPos).name() in PRISMARINE_BLOCKS) count++
                }
            }
        }
        return count >= 16
    }

    private fun applyConduitPower(player: Player) {
        player.addEffect(Potion(PotionEffect.CONDUIT_POWER, 0, 260, Potion.ICON_FLAG))
    }
}
