package me.nebula.orbit.mechanic.sculkcatalyst

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.entity.EntityDeathEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.sqrt

private const val CATALYST_RANGE = 8
private const val SCULK_SPREAD_RADIUS = 3

class SculkCatalystModule : OrbitModule("sculk-catalyst") {

    private var tickTask: Task? = null
    private val recentDeaths = ConcurrentLinkedQueue<DeathRecord>()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(EntityDeathEvent::class.java) { event ->
            val entity = event.entity
            val instance = entity.instance ?: return@addListener
            recentDeaths.add(DeathRecord(instance, entity.position, System.currentTimeMillis()))
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        recentDeaths.clear()
        super.onDisable()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        recentDeaths.removeIf { now - it.timestamp > 5000L }

        val processed = mutableListOf<DeathRecord>()

        recentDeaths.forEach { death ->
            val instance = death.instance
            val dx = death.position.blockX()
            val dy = death.position.blockY()
            val dz = death.position.blockZ()

            for (x in (dx - CATALYST_RANGE)..(dx + CATALYST_RANGE)) {
                for (y in (dy - CATALYST_RANGE)..(dy + CATALYST_RANGE)) {
                    for (z in (dz - CATALYST_RANGE)..(dz + CATALYST_RANGE)) {
                        val block = instance.getBlock(x, y, z)
                        if (block.name() != "minecraft:sculk_catalyst") continue

                        val dist = sqrt(
                            ((dx - x) * (dx - x) + (dy - y) * (dy - y) + (dz - z) * (dz - z)).toDouble()
                        )
                        if (dist > CATALYST_RANGE) continue

                        spreadSculk(instance, death.position)
                        processed.add(death)
                    }
                }
            }
        }

        recentDeaths.removeAll(processed.toSet())
    }

    private fun spreadSculk(instance: net.minestom.server.instance.Instance, deathPos: Pos) {
        val bx = deathPos.blockX()
        val by = deathPos.blockY()
        val bz = deathPos.blockZ()

        for (x in (bx - SCULK_SPREAD_RADIUS)..(bx + SCULK_SPREAD_RADIUS)) {
            for (z in (bz - SCULK_SPREAD_RADIUS)..(bz + SCULK_SPREAD_RADIUS)) {
                for (y in by downTo (by - 2)) {
                    val existing = instance.getBlock(x, y, z)
                    if (existing.isAir) continue
                    val above = instance.getBlock(x, y + 1, z)
                    if (!above.isAir && above.name() != "minecraft:sculk_vein") continue
                    instance.setBlock(x, y, z, Block.SCULK)
                    break
                }
            }
        }
    }
}

private data class DeathRecord(
    val instance: net.minestom.server.instance.Instance,
    val position: Pos,
    val timestamp: Long,
)
