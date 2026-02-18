package me.nebula.orbit.mechanic.gravity

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.TaskSchedule

private val GRAVITY_BLOCKS = setOf(
    "minecraft:sand", "minecraft:red_sand", "minecraft:gravel",
    "minecraft:anvil", "minecraft:chipped_anvil", "minecraft:damaged_anvil",
    "minecraft:dragon_egg", "minecraft:concrete_powder",
)

class GravityModule : OrbitModule("gravity") {

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val above = event.blockPosition.add(0, 1, 0)
            checkAndFall(instance, above)
        }

        eventNode.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            if (isGravityBlock(event.block)) {
                val below = instance.getBlock(pos.add(0, -1, 0))
                if (below == Block.AIR) {
                    net.minestom.server.MinecraftServer.getSchedulerManager().buildTask {
                        startFall(instance, pos, event.block)
                    }.delay(TaskSchedule.tick(2)).schedule()
                }
            }
        }
    }

    private fun checkAndFall(instance: Instance, pos: net.minestom.server.coordinate.Point) {
        val block = instance.getBlock(pos)
        if (!isGravityBlock(block)) return
        startFall(instance, pos, block)
    }

    private fun startFall(instance: Instance, pos: net.minestom.server.coordinate.Point, block: Block) {
        instance.setBlock(pos, Block.AIR)

        val entity = Entity(EntityType.FALLING_BLOCK)
        entity.setNoGravity(false)
        entity.setInstance(instance, Pos(pos.x() + 0.5, pos.y().toDouble(), pos.z() + 0.5))

        entity.scheduler().buildTask {
            tickFallingBlock(entity, block)
        }.repeat(TaskSchedule.tick(1)).schedule()

        entity.scheduler().buildTask {
            if (!entity.isRemoved) {
                landBlock(entity, block)
            }
        }.delay(TaskSchedule.seconds(30)).schedule()
    }

    private fun tickFallingBlock(entity: Entity, block: Block) {
        if (entity.isRemoved) return
        val instance = entity.instance ?: return

        if (entity.isOnGround || entity.velocity.y() == 0.0) {
            landBlock(entity, block)
        }
    }

    private fun landBlock(entity: Entity, block: Block) {
        if (entity.isRemoved) return
        val instance = entity.instance ?: return
        val pos = entity.position
        val blockPos = Vec(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble())

        if (instance.getBlock(blockPos) == Block.AIR) {
            instance.setBlock(blockPos, block)
        }
        entity.remove()
    }

    private fun isGravityBlock(block: Block): Boolean =
        block.name() in GRAVITY_BLOCKS || block.name().endsWith("_concrete_powder")
}
