package me.nebula.orbit.mechanic.amethyst

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private data class AmethystKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

private val GROWTH_STAGES = listOf(
    "minecraft:small_amethyst_bud",
    "minecraft:medium_amethyst_bud",
    "minecraft:large_amethyst_bud",
    "minecraft:amethyst_cluster",
)

class AmethystModule : OrbitModule("amethyst") {

    private val buds = ConcurrentHashMap.newKeySet<AmethystKey>()

    override fun onEnable() {
        super.onEnable()

        buds.cleanOnInstanceRemove { it.instanceHash }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.name() !in GROWTH_STAGES) return@addListener
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            buds.add(AmethystKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
        }

        MinecraftServer.getSchedulerManager().buildTask {
            val iterator = buds.iterator()
            while (iterator.hasNext()) {
                val key = iterator.next()
                if (Random.nextFloat() > 0.02f) continue

                val instance = MinecraftServer.getInstanceManager().instances
                    .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: run {
                    iterator.remove()
                    continue
                }

                val block = instance.getBlock(key.x, key.y, key.z)
                val currentStage = GROWTH_STAGES.indexOf(block.name())
                if (currentStage < 0 || currentStage >= GROWTH_STAGES.lastIndex) {
                    iterator.remove()
                    continue
                }

                val nextBlock = Block.fromKey(GROWTH_STAGES[currentStage + 1])
                if (nextBlock != null) {
                    var grown = nextBlock
                    block.getProperty("facing")?.let { grown = grown.withProperty("facing", it) }
                    instance.setBlock(key.x, key.y, key.z, grown)
                }

                if (currentStage + 1 >= GROWTH_STAGES.lastIndex) {
                    iterator.remove()
                }
            }
        }.repeat(TaskSchedule.tick(200)).schedule()
    }

    override fun onDisable() {
        buds.clear()
        super.onDisable()
    }
}
