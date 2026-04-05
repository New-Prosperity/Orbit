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
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val CROP_MAX_AGE = mapOf(
    "minecraft:wheat" to 7,
    "minecraft:carrots" to 7,
    "minecraft:potatoes" to 7,
    "minecraft:beetroots" to 3,
    "minecraft:nether_wart" to 3,
    "minecraft:sweet_berry_bush" to 3,
    "minecraft:melon_stem" to 7,
    "minecraft:pumpkin_stem" to 7,
    "minecraft:torchflower_crop" to 1,
)

private val CROP_REQUIRED_SOIL = mapOf(
    "minecraft:wheat" to "minecraft:farmland",
    "minecraft:carrots" to "minecraft:farmland",
    "minecraft:potatoes" to "minecraft:farmland",
    "minecraft:beetroots" to "minecraft:farmland",
    "minecraft:melon_stem" to "minecraft:farmland",
    "minecraft:pumpkin_stem" to "minecraft:farmland",
    "minecraft:torchflower_crop" to "minecraft:farmland",
    "minecraft:nether_wart" to "minecraft:soul_sand",
)

private val CROP_SEED_TO_CROP = mapOf(
    "minecraft:wheat_seeds" to "minecraft:wheat",
    "minecraft:carrot" to "minecraft:carrots",
    "minecraft:potato" to "minecraft:potatoes",
    "minecraft:beetroot_seeds" to "minecraft:beetroots",
    "minecraft:melon_seeds" to "minecraft:melon_stem",
    "minecraft:pumpkin_seeds" to "minecraft:pumpkin_stem",
    "minecraft:nether_wart" to "minecraft:nether_wart",
    "minecraft:torchflower_seeds" to "minecraft:torchflower_crop",
)

object CropGrowthModule : VanillaModule {

    override val id = "crop-growth"
    override val description = "Crops require farmland/soul sand to be placed. Grow over time via random tick simulation."
    override val configParams = listOf(
        ConfigParam.IntParam("tickRate", "Game ticks between growth checks", 100, 20, 1200),
        ConfigParam.IntParam("growthChance", "Percent chance per crop per tick to grow one stage", 5, 1, 100),
    )

    override fun createNode(instance: Instance, config: ModuleConfig): EventNode<Event> {
        val tickRate = config.getInt("tickRate", 100)
        val growthChance = config.getInt("growthChance", 5)

        val trackedCrops = ConcurrentHashMap.newKeySet<Long>()

        val node = EventNode.all("vanilla-crop-growth")

        node.addListener(PlayerBlockPlaceEvent::class.java) { event ->
            val blockName = event.block.name()
            val requiredSoil = CROP_REQUIRED_SOIL[blockName]

            if (requiredSoil != null) {
                val below = event.instance.getBlock(
                    event.blockPosition.blockX(),
                    event.blockPosition.blockY() - 1,
                    event.blockPosition.blockZ(),
                )
                if (below.name() != requiredSoil) {
                    event.isCancelled = true
                    return@addListener
                }
                trackedCrops.add(packBlockPos(event.blockPosition.blockX(), event.blockPosition.blockY(), event.blockPosition.blockZ()))
            }
        }

        node.addListener(PlayerBlockBreakEvent::class.java) { event ->
            if (event.block.name() == "minecraft:farmland" || event.block.name() == "minecraft:soul_sand") {
                val above = event.instance.getBlock(
                    event.blockPosition.blockX(),
                    event.blockPosition.blockY() + 1,
                    event.blockPosition.blockZ(),
                )
                if (above.name() in CROP_MAX_AGE) {
                    event.instance.setBlock(
                        event.blockPosition.blockX(),
                        event.blockPosition.blockY() + 1,
                        event.blockPosition.blockZ(),
                        Block.AIR,
                    )
                    trackedCrops.remove(packBlockPos(
                        event.blockPosition.blockX(),
                        event.blockPosition.blockY() + 1,
                        event.blockPosition.blockZ(),
                    ))
                }
            }
        }

        var tickCount = 0
        lateinit var task: Task
        task = instance.scheduler().buildTask {
            if (!VanillaModules.isEnabled(instance, "crop-growth")) {
                task.cancel()
                return@buildTask
            }
            tickCount++
            if (tickCount % tickRate != 0) return@buildTask

            val toRemove = mutableListOf<Long>()

            for (packed in trackedCrops) {
                val x = unpackBlockX(packed)
                val y = unpackBlockY(packed)
                val z = unpackBlockZ(packed)

                val block = instance.getBlock(x, y, z)
                val blockName = block.name()
                val maxAge = CROP_MAX_AGE[blockName]

                if (maxAge == null) {
                    toRemove.add(packed)
                    continue
                }

                val currentAge = block.getProperty("age")?.toIntOrNull() ?: 0
                if (currentAge >= maxAge) {
                    toRemove.add(packed)
                    continue
                }

                if (Random.nextInt(100) >= growthChance) continue

                val requiredSoil = CROP_REQUIRED_SOIL[blockName] ?: continue
                val below = instance.getBlock(x, y - 1, z)
                if (below.name() != requiredSoil) {
                    toRemove.add(packed)
                    continue
                }

                instance.setBlock(x, y, z, block.withProperty("age", (currentAge + 1).toString()))
                if (currentAge + 1 >= maxAge) toRemove.add(packed)
            }

            for (packed in toRemove) trackedCrops.remove(packed)
        }.repeat(Duration.ofMillis(50)).schedule()

        return node
    }

}
