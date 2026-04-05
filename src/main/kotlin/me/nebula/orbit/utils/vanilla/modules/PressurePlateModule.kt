package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.sound.playSound
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.VanillaModules
import me.nebula.orbit.utils.vanilla.packBlockPos
import me.nebula.orbit.utils.vanilla.unpackBlockX
import me.nebula.orbit.utils.vanilla.unpackBlockY
import me.nebula.orbit.utils.vanilla.unpackBlockZ
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.EntityTracker
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val WOODEN_PLATES = setOf(
    "minecraft:oak_pressure_plate", "minecraft:spruce_pressure_plate",
    "minecraft:birch_pressure_plate", "minecraft:jungle_pressure_plate",
    "minecraft:acacia_pressure_plate", "minecraft:dark_oak_pressure_plate",
    "minecraft:mangrove_pressure_plate", "minecraft:cherry_pressure_plate",
    "minecraft:bamboo_pressure_plate", "minecraft:crimson_pressure_plate",
    "minecraft:warped_pressure_plate",
)

private val STONE_PLATES = setOf(
    "minecraft:stone_pressure_plate", "minecraft:polished_blackstone_pressure_plate",
)

private val WEIGHTED_PLATES = setOf(
    "minecraft:light_weighted_pressure_plate", "minecraft:heavy_weighted_pressure_plate",
)

private val ALL_PLATES = WOODEN_PLATES + STONE_PLATES + WEIGHTED_PLATES

object PressurePlateModule : VanillaModule {

    override val id = "pressure-plate"
    override val description = "Pressure plates activate on entity/player contact (wood=all entities, stone=players+mobs only)"

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val activePlates = ConcurrentHashMap<Long, Long>()

        val node = EventNode.all("vanilla-pressure-plate")

        node.addListener(PlayerMoveEvent::class.java) { event ->
            val player = event.player
            val bx = player.position.blockX()
            val by = player.position.blockY()
            val bz = player.position.blockZ()

            val block = event.instance.getBlock(bx, by, bz)
            val blockName = block.name()

            if (blockName !in ALL_PLATES) {
                val belowBlock = event.instance.getBlock(bx, by - 1, bz)
                val belowName = belowBlock.name()
                if (belowName in ALL_PLATES) {
                    tryActivate(event.instance, bx, by - 1, bz, belowBlock, belowName, activePlates, player)
                }
                return@addListener
            }

            tryActivate(event.instance, bx, by, bz, block, blockName, activePlates, player)
        }

        lateinit var task: Task
        task = instance.scheduler().buildTask {
            if (!VanillaModules.isEnabled(instance, "pressure-plate")) {
                task.cancel()
                return@buildTask
            }

            val now = System.currentTimeMillis()
            val toDeactivate = mutableListOf<Long>()

            for ((packed, activatedAt) in activePlates) {
                if (now - activatedAt < 500) continue

                val x = unpackBlockX(packed)
                val y = unpackBlockY(packed)
                val z = unpackBlockZ(packed)
                val block = instance.getBlock(x, y, z)

                if (block.name() !in ALL_PLATES) {
                    toDeactivate.add(packed)
                    continue
                }

                var entityPresent = false
                instance.entityTracker.chunkEntities(x shr 4, z shr 4, EntityTracker.Target.ENTITIES).forEach { entity ->
                    if (!entityPresent && entity.position.blockX() == x && entity.position.blockZ() == z) {
                        val ey = entity.position.blockY()
                        if (ey == y || ey == y + 1) {
                            entityPresent = true
                        }
                    }
                }

                if (!entityPresent) {
                    toDeactivate.add(packed)
                    instance.setBlock(x, y, z, block.withProperty("powered", "false"))
                }
            }

            for (packed in toDeactivate) activePlates.remove(packed)
        }.repeat(Duration.ofMillis(1000)).schedule()

        return node
    }

    private fun tryActivate(
        instance: Instance,
        x: Int,
        y: Int,
        z: Int,
        block: Block,
        blockName: String,
        activePlates: ConcurrentHashMap<Long, Long>,
        player: Player,
    ) {
        val powered = block.getProperty("powered") ?: "false"
        if (powered == "true") {
            activePlates[packBlockPos(x, y, z)] = System.currentTimeMillis()
            return
        }

        instance.setBlock(x, y, z, block.withProperty("powered", "true"))
        activePlates[packBlockPos(x, y, z)] = System.currentTimeMillis()
        player.playSound(SoundEvent.BLOCK_STONE_PRESSURE_PLATE_CLICK_ON)
    }
}
