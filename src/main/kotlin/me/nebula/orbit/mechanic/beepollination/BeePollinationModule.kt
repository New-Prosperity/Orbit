package me.nebula.orbit.mechanic.beepollination

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val HAS_NECTAR_TAG = Tag.Boolean("mechanic:bee_pollination:has_nectar").defaultValue(false)

private const val HIVE_SEARCH_RANGE = 32
private const val FLOWER_RANGE = 2

private val FLOWER_BLOCKS = setOf(
    "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid",
    "minecraft:allium", "minecraft:azure_bluet", "minecraft:red_tulip",
    "minecraft:orange_tulip", "minecraft:white_tulip", "minecraft:pink_tulip",
    "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
    "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
    "minecraft:peony", "minecraft:torchflower", "minecraft:wither_rose",
)

private val HIVE_BLOCKS = setOf("minecraft:beehive", "minecraft:bee_nest")

class BeePollinationModule : OrbitModule("bee-pollination") {

    private var tickTask: Task? = null
    private val trackedBees: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(100))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedBees.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedBees.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.BEE) return@entityLoop
                trackedBees.add(entity)
            }
        }

        trackedBees.forEach { bee ->
            if (bee.isRemoved) return@forEach
            val instance = bee.instance ?: return@forEach
            val hasNectar = bee.getTag(HAS_NECTAR_TAG)

            if (!hasNectar) {
                if (isNearFlower(bee, instance)) {
                    bee.setTag(HAS_NECTAR_TAG, true)
                }
            } else {
                val hivePos = findNearbyHive(bee, instance)
                if (hivePos != null) {
                    val block = instance.getBlock(hivePos[0], hivePos[1], hivePos[2])
                    val honeyLevel = block.getProperty("honey_level")?.toIntOrNull() ?: 0
                    if (honeyLevel < 5) {
                        instance.setBlock(
                            hivePos[0], hivePos[1], hivePos[2],
                            block.withProperty("honey_level", (honeyLevel + 1).toString()),
                        )
                    }
                    bee.setTag(HAS_NECTAR_TAG, false)
                }
            }
        }
    }

    private fun isNearFlower(bee: Entity, instance: Instance): Boolean {
        val pos = bee.position
        for (dx in -FLOWER_RANGE..FLOWER_RANGE) {
            for (dy in -FLOWER_RANGE..FLOWER_RANGE) {
                for (dz in -FLOWER_RANGE..FLOWER_RANGE) {
                    val block = instance.getBlock(pos.blockX() + dx, pos.blockY() + dy, pos.blockZ() + dz)
                    if (block.name() in FLOWER_BLOCKS) return true
                }
            }
        }
        return false
    }

    private fun findNearbyHive(bee: Entity, instance: Instance): IntArray? {
        val pos = bee.position
        val range = HIVE_SEARCH_RANGE.coerceAtMost(8)
        for (dx in -range..range) {
            for (dy in -4..4) {
                for (dz in -range..range) {
                    val bx = pos.blockX() + dx
                    val by = pos.blockY() + dy
                    val bz = pos.blockZ() + dz
                    val block = instance.getBlock(bx, by, bz)
                    if (block.name() in HIVE_BLOCKS) return intArrayOf(bx, by, bz)
                }
            }
        }
        return null
    }
}
