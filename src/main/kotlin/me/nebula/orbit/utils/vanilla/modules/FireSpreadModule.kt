package me.nebula.orbit.utils.vanilla.modules

import me.nebula.orbit.utils.vanilla.ConfigParam
import me.nebula.orbit.utils.vanilla.ModuleConfig
import me.nebula.orbit.utils.vanilla.VanillaModule
import me.nebula.orbit.utils.vanilla.VanillaModules
import me.nebula.orbit.utils.vanilla.packBlockPos
import me.nebula.orbit.utils.vanilla.unpackBlockX
import me.nebula.orbit.utils.vanilla.unpackBlockY
import me.nebula.orbit.utils.vanilla.unpackBlockZ
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.event.player.PlayerBlockPlaceEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.timer.Task
import java.time.Duration
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random

private val FLAMMABLE_NAMES = setOf(
    "minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:birch_planks",
    "minecraft:jungle_planks", "minecraft:acacia_planks", "minecraft:dark_oak_planks",
    "minecraft:mangrove_planks", "minecraft:cherry_planks", "minecraft:bamboo_planks",
    "minecraft:oak_log", "minecraft:spruce_log", "minecraft:birch_log",
    "minecraft:jungle_log", "minecraft:acacia_log", "minecraft:dark_oak_log",
    "minecraft:oak_wood", "minecraft:spruce_wood", "minecraft:birch_wood",
    "minecraft:jungle_wood", "minecraft:acacia_wood", "minecraft:dark_oak_wood",
    "minecraft:oak_leaves", "minecraft:spruce_leaves", "minecraft:birch_leaves",
    "minecraft:jungle_leaves", "minecraft:acacia_leaves", "minecraft:dark_oak_leaves",
    "minecraft:white_wool", "minecraft:orange_wool", "minecraft:magenta_wool",
    "minecraft:light_blue_wool", "minecraft:yellow_wool", "minecraft:lime_wool",
    "minecraft:pink_wool", "minecraft:gray_wool", "minecraft:light_gray_wool",
    "minecraft:cyan_wool", "minecraft:purple_wool", "minecraft:blue_wool",
    "minecraft:brown_wool", "minecraft:green_wool", "minecraft:red_wool",
    "minecraft:black_wool", "minecraft:bookshelf", "minecraft:tnt",
    "minecraft:white_carpet", "minecraft:orange_carpet", "minecraft:magenta_carpet",
    "minecraft:light_blue_carpet", "minecraft:yellow_carpet", "minecraft:lime_carpet",
    "minecraft:pink_carpet", "minecraft:gray_carpet", "minecraft:light_gray_carpet",
    "minecraft:cyan_carpet", "minecraft:purple_carpet", "minecraft:blue_carpet",
    "minecraft:brown_carpet", "minecraft:green_carpet", "minecraft:red_carpet",
    "minecraft:black_carpet", "minecraft:vine", "minecraft:hay_block",
    "minecraft:dried_kelp_block", "minecraft:bamboo", "minecraft:scaffolding",
    "minecraft:oak_fence", "minecraft:spruce_fence", "minecraft:birch_fence",
    "minecraft:jungle_fence", "minecraft:acacia_fence", "minecraft:dark_oak_fence",
    "minecraft:oak_stairs", "minecraft:spruce_stairs", "minecraft:birch_stairs",
    "minecraft:jungle_stairs", "minecraft:acacia_stairs", "minecraft:dark_oak_stairs",
    "minecraft:oak_slab", "minecraft:spruce_slab", "minecraft:birch_slab",
    "minecraft:jungle_slab", "minecraft:acacia_slab", "minecraft:dark_oak_slab",
    "minecraft:oak_fence_gate", "minecraft:spruce_fence_gate", "minecraft:birch_fence_gate",
    "minecraft:jungle_fence_gate", "minecraft:acacia_fence_gate", "minecraft:dark_oak_fence_gate",
    "minecraft:oak_door", "minecraft:spruce_door", "minecraft:birch_door",
    "minecraft:jungle_door", "minecraft:acacia_door", "minecraft:dark_oak_door",
)

private val DX = intArrayOf(-1, 1, 0, 0, 0, 0)
private val DY = intArrayOf(0, 0, -1, 1, 0, 0)
private val DZ = intArrayOf(0, 0, 0, 0, -1, 1)


object FireSpreadModule : VanillaModule {

    override val id = "fire-spread"
    override val description = "Fire spreads to adjacent flammable blocks, burns out over time"
    override val configParams = listOf(
        ConfigParam.IntParam("spreadChance", "Percent chance fire spreads per tick per neighbor", 15, 0, 100),
        ConfigParam.IntParam("burnOutTicks", "Ticks before fire burns out on non-flammable surface", 600, 100, 6000),
        ConfigParam.IntParam("tickRate", "Game ticks between fire spread checks", 30, 5, 200),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val spreadChance = config.getInt("spreadChance", 15)
        val burnOutTicks = config.getInt("burnOutTicks", 600)
        val tickRate = config.getInt("tickRate", 30)

        val fireBlocks = ConcurrentLinkedQueue<Long>()
        val fireAge = HashMap<Long, Int>()

        val node = EventNode.all("vanilla-fire-spread")

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            if (event.block.compare(Block.FIRE) || event.block.compare(Block.SOUL_FIRE)) {
                val x = event.blockPosition.blockX()
                val y = event.blockPosition.blockY()
                val z = event.blockPosition.blockZ()
                val packed = packBlockPos(x, y, z)
                fireBlocks.add(packed)
                fireAge[packed] = 0
            }
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.compare(Block.FIRE) || event.block.compare(Block.SOUL_FIRE)) {
                val packed = packBlockPos(event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ())
                fireAge.remove(packed)
            }
        }

        var tickCount = 0
        lateinit var task: Task
        task = instance.scheduler().buildTask {
            if (!VanillaModules.isEnabled(instance, "fire-spread")) {
                task.cancel()
                return@buildTask
            }
            tickCount++
            if (tickCount % tickRate != 0) return@buildTask

            val toProcess = mutableListOf<Long>()
            val toRemove = mutableListOf<Long>()
            val toAdd = mutableListOf<Long>()

            while (fireBlocks.isNotEmpty()) {
                toProcess.add(fireBlocks.poll() ?: break)
            }

            for (packed in toProcess) {
                val x = unpackBlockX(packed)
                val y = unpackBlockY(packed)
                val z = unpackBlockZ(packed)
                val block = instance.getBlock(x, y, z)

                if (!block.compare(Block.FIRE) && !block.compare(Block.SOUL_FIRE)) {
                    fireAge.remove(packed)
                    continue
                }

                val age = (fireAge[packed] ?: 0) + 1
                fireAge[packed] = age

                val belowName = instance.getBlock(x, y - 1, z).name()
                val onFlammable = belowName in FLAMMABLE_NAMES

                if (!onFlammable && age > burnOutTicks / tickRate) {
                    instance.setBlock(x, y, z, Block.AIR)
                    fireAge.remove(packed)
                    continue
                }

                if (onFlammable && age > (burnOutTicks * 2) / tickRate) {
                    instance.setBlock(x, y - 1, z, Block.AIR)
                }

                for (i in DX.indices) {
                    val nx = x + DX[i]
                    val ny = y + DY[i]
                    val nz = z + DZ[i]
                    val neighbor = instance.getBlock(nx, ny, nz)

                    if (neighbor.name() in FLAMMABLE_NAMES && Random.nextInt(100) < spreadChance) {
                        val aboveNeighbor = instance.getBlock(nx, ny + 1, nz)
                        if (aboveNeighbor.isAir) {
                            val newPacked = packBlockPos(nx, ny + 1, nz)
                            instance.setBlock(nx, ny + 1, nz, Block.FIRE)
                            toAdd.add(newPacked)
                            fireAge[newPacked] = 0
                        }
                    }
                }

                toAdd.add(packed)
            }

            for (packed in toAdd) fireBlocks.add(packed)
        }.repeat(Duration.ofMillis(50)).schedule()

        return node
    }
}
