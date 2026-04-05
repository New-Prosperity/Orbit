package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.liquidflow.LiquidFlowEngine
import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.VanillaModules
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockInteractEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

object WaterFlowModule : VanillaModule {

    override val id = "water-flow"
    override val description = "Water source blocks spread and flow with level-based mechanics, infinite source creation"
    override val configParams = listOf(
        ConfigParam.BoolParam("infiniteSource", "Two adjacent water sources create a new source block", true),
        ConfigParam.IntParam("tickRate", "Game ticks between flow updates (1 tick = 50ms)", 5, 1, 100),
    )

    private val states = ConcurrentHashMap<Long, FlowState>()

    private class FlowState(val engine: LiquidFlowEngine, val task: Task)

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val key = System.identityHashCode(instance).toLong()
        states.remove(key)?.task?.cancel()

        val infiniteSource = config.getBoolean("infiniteSource", true)
        val tickRate = config.getInt("tickRate", 5)

        val engine = LiquidFlowEngine(instance, Block.WATER, 7, infiniteSource, opposingLiquid = Block.LAVA)

        var tickCount = 0
        lateinit var task: Task
        task = instance.scheduler().buildTask {
            if (!VanillaModules.isEnabled(instance, "water-flow")) {
                task.cancel()
                states.remove(key)
                return@buildTask
            }
            tickCount++
            if (tickCount % tickRate == 0) engine.tick()
        }.repeat(Duration.ofMillis(50)).schedule()

        states[key] = FlowState(engine, task)

        val node = EventNode.all("vanilla-water-flow")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val material = event.player.itemInMainHand.material()
            when (material) {
                Material.WATER_BUCKET -> {
                    val pos = event.blockPosition.relative(event.blockFace)
                    engine.notifyBlockChanged(pos.blockX(), pos.blockY(), pos.blockZ())
                }
                Material.BUCKET -> {
                    if (event.block.compare(Block.WATER)) {
                        val x = event.blockPosition.blockX()
                        val y = event.blockPosition.blockY()
                        val z = event.blockPosition.blockZ()
                        engine.notifyBlockChanged(x, y, z)
                    }
                }
                else -> {}
            }
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val x = event.blockPosition.blockX()
            val y = event.blockPosition.blockY()
            val z = event.blockPosition.blockZ()
            engine.notifyBlockChanged(x, y, z)
        }

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val x = event.blockPosition.blockX()
            val y = event.blockPosition.blockY()
            val z = event.blockPosition.blockZ()
            engine.scheduleNeighborUpdates(x, y, z)
        }

        return node
    }
}
