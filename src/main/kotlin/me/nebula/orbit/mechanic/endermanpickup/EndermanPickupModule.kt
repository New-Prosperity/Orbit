package me.nebula.orbit.mechanic.endermanpickup

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
import kotlin.random.Random

private val HELD_BLOCK_TAG = Tag.String("mechanic:enderman_pickup:held_block").defaultValue("")
private val LAST_ACTION_TAG = Tag.Long("mechanic:enderman_pickup:last_action").defaultValue(0L)

private const val ACTION_COOLDOWN_MS = 5000L
private const val PICKUP_RANGE = 4

private val PICKUPABLE_BLOCKS = listOf(
    Block.GRASS_BLOCK, Block.DIRT, Block.SAND, Block.GRAVEL,
    Block.CLAY, Block.PUMPKIN, Block.MELON, Block.MYCELIUM,
    Block.PODZOL, Block.RED_MUSHROOM, Block.BROWN_MUSHROOM,
    Block.CACTUS, Block.TNT, Block.CRIMSON_NYLIUM, Block.WARPED_NYLIUM,
)

private val BLOCK_BY_NAME: Map<String, Block> = PICKUPABLE_BLOCKS.associateBy { it.name() }

class EndermanPickupModule : OrbitModule("enderman-pickup") {

    private var tickTask: Task? = null
    private val trackedEndermen: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(40))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedEndermen.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedEndermen.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.ENDERMAN) return@entityLoop
                trackedEndermen.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedEndermen.forEach { enderman ->
            val lastAction = enderman.getTag(LAST_ACTION_TAG)
            if (now - lastAction < ACTION_COOLDOWN_MS) return@forEach

            if (Random.nextInt(3) != 0) return@forEach

            val heldBlock = enderman.getTag(HELD_BLOCK_TAG)
            val instance = enderman.instance ?: return@forEach

            if (heldBlock.isEmpty()) {
                tryPickup(enderman, instance, now)
            } else {
                tryPlace(enderman, instance, heldBlock, now)
            }
        }
    }

    private fun tryPickup(enderman: Entity, instance: Instance, now: Long) {
        val pos = enderman.position
        for (attempt in 0 until 5) {
            val x = pos.blockX() + Random.nextInt(-PICKUP_RANGE, PICKUP_RANGE + 1)
            val y = pos.blockY() + Random.nextInt(-1, 2)
            val z = pos.blockZ() + Random.nextInt(-PICKUP_RANGE, PICKUP_RANGE + 1)

            val block = instance.getBlock(x, y, z)
            if (block in PICKUPABLE_BLOCKS) {
                instance.setBlock(x, y, z, Block.AIR)
                enderman.setTag(HELD_BLOCK_TAG, block.name())
                enderman.setTag(LAST_ACTION_TAG, now)
                return
            }
        }
    }

    private fun tryPlace(enderman: Entity, instance: Instance, blockName: String, now: Long) {
        val pos = enderman.position
        for (attempt in 0 until 5) {
            val x = pos.blockX() + Random.nextInt(-PICKUP_RANGE, PICKUP_RANGE + 1)
            val y = pos.blockY() + Random.nextInt(0, 2)
            val z = pos.blockZ() + Random.nextInt(-PICKUP_RANGE, PICKUP_RANGE + 1)

            if (instance.getBlock(x, y, z) == Block.AIR && instance.getBlock(x, y - 1, z) != Block.AIR) {
                val block = BLOCK_BY_NAME[blockName] ?: return
                instance.setBlock(x, y, z, block)
                enderman.setTag(HELD_BLOCK_TAG, "")
                enderman.setTag(LAST_ACTION_TAG, now)
                return
            }
        }
    }
}
