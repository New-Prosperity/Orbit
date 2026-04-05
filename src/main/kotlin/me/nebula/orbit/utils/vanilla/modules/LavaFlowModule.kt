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

object LavaFlowModule : VanillaModule {

    override val id = "lava-flow"
    override val description = "Lava source blocks spread and flow (slower, shorter range than water)"
    override val configParams = listOf(
        ConfigParam.IntParam("maxLevel", "Maximum flow distance from source", 3, 1, 7),
        ConfigParam.IntParam("tickRate", "Game ticks between flow updates", 30, 1, 200),
    )

    private val states = ConcurrentHashMap<Long, FlowState>()

    private class FlowState(val engine: LiquidFlowEngine, val task: Task)

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val key = System.identityHashCode(instance).toLong()
        states.remove(key)?.task?.cancel()

        val maxLevel = config.getInt("maxLevel", 3)
        val tickRate = config.getInt("tickRate", 30)

        val engine = LiquidFlowEngine(instance, Block.LAVA, maxLevel, false, opposingLiquid = Block.WATER)

        var tickCount = 0
        lateinit var task: Task
        task = instance.scheduler().buildTask {
            if (!VanillaModules.isEnabled(instance, "lava-flow")) {
                task.cancel()
                states.remove(key)
                return@buildTask
            }
            tickCount++
            if (tickCount % tickRate == 0) engine.tick()
        }.repeat(Duration.ofMillis(50)).schedule()

        states[key] = FlowState(engine, task)

        val node = EventNode.all("vanilla-lava-flow")

        node.addListener(PlayerBlockInteractEvent::class.java) { event ->
            val material = event.player.itemInMainHand.material()
            when (material) {
                Material.LAVA_BUCKET -> {
                    val pos = event.blockPosition.relative(event.blockFace)
                    engine.notifyBlockChanged(pos.blockX(), pos.blockY(), pos.blockZ())
                }
                Material.BUCKET -> {
                    if (event.block.compare(Block.LAVA)) {
                        engine.notifyBlockChanged(event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ())
                    }
                }
                else -> {}
            }
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            engine.notifyBlockChanged(event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ())
        }

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            engine.scheduleNeighborUpdates(event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ())
        }

        return node
    }
}
