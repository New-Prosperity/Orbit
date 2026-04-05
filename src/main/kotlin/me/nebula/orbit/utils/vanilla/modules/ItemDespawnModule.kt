package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.VanillaModules
import net.minestom.server.entity.ItemEntity
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import java.time.Duration

object ItemDespawnModule : VanillaModule {

    override val id = "item-despawn"
    override val description = "Item entities despawn after 5 minutes (6000 ticks), merge nearby identical stacks"
    override val configParams = listOf(
        ConfigParam.IntParam("despawnTicks", "Ticks before item entities despawn (6000 = 5min)", 6000, 600, 72000),
        ConfigParam.BoolParam("merge", "Merge nearby identical item stacks", true),
        ConfigParam.DoubleParam("mergeRadius", "Block radius for item merging", 0.5, 0.25, 3.0),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val despawnTicks = config.getInt("despawnTicks", 6000).toLong()
        val merge = config.getBoolean("merge", true)
        val mergeRadiusSq = config.getDouble("mergeRadius", 0.5).let { it * it }

        val node = EventNode.all("vanilla-item-despawn")

        lateinit var task: Task
        task = instance.scheduler().buildTask {
            if (!VanillaModules.isEnabled(instance, "item-despawn")) {
                task.cancel()
                return@buildTask
            }

            val items = if (merge) ArrayList<ItemEntity>(64) else null
            for (entity in instance.entityTracker.entities(EntityTracker.Target.ITEMS)) {
                if (entity.isRemoved) continue
                if (entity.aliveTicks >= despawnTicks) {
                    entity.remove()
                    continue
                }
                items?.add(entity)
            }

            if (items != null && items.size > 1) {
                var i = 0
                while (i < items.size) {
                    val a = items[i]
                    if (a.isRemoved) { i++; continue }
                    val stackA = a.itemStack
                    if (stackA.amount() >= 64) { i++; continue }
                    var j = i + 1
                    while (j < items.size) {
                        val b = items[j]
                        if (b.isRemoved) { j++; continue }
                        val dx = a.position.x() - b.position.x()
                        val dy = a.position.y() - b.position.y()
                        val dz = a.position.z() - b.position.z()
                        if (dx * dx + dy * dy + dz * dz > mergeRadiusSq) { j++; continue }
                        val stackB = b.itemStack
                        if (stackA.material() == stackB.material() && stackA.amount() + stackB.amount() <= 64) {
                            a.itemStack = stackA.withAmount(stackA.amount() + stackB.amount())
                            b.remove()
                        }
                        j++
                    }
                    i++
                }
            }
        }.repeat(Duration.ofMillis(2000)).schedule()

        return node
    }
}
