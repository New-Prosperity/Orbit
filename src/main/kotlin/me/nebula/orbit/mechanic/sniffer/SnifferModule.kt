package me.nebula.orbit.mechanic.sniffer

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import kotlin.random.Random

private val LAST_DIG_TAG: Tag<Long> = Tag.Long("sniffer_last_dig")
private const val DIG_COOLDOWN_MS = 120_000L

private val DIGGABLE_BLOCKS = setOf(
    "minecraft:dirt",
    "minecraft:grass_block",
    "minecraft:coarse_dirt",
    "minecraft:rooted_dirt",
    "minecraft:mud",
    "minecraft:muddy_mangrove_roots",
)

private val DIG_DROPS = listOf(
    Material.TORCHFLOWER_SEEDS,
    Material.PITCHER_POD,
)

class SnifferModule : OrbitModule("sniffer") {

    private var tickTask: Task? = null

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
        super.onDisable()
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        MinecraftServer.getInstanceManager().instances.forEach { instance ->
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.SNIFFER) return@entityLoop

                val lastDig = entity.getTag(LAST_DIG_TAG) ?: 0L
                if (now - lastDig < DIG_COOLDOWN_MS) return@entityLoop

                val pos = entity.position
                val blockBelow = instance.getBlock(pos.blockX(), pos.blockY() - 1, pos.blockZ())
                if (blockBelow.name() !in DIGGABLE_BLOCKS) return@entityLoop

                if (Random.nextFloat() > 0.3f) return@entityLoop

                entity.setTag(LAST_DIG_TAG, now)

                val drop = DIG_DROPS[Random.nextInt(DIG_DROPS.size)]
                val itemEntity = ItemEntity(ItemStack.of(drop))
                itemEntity.setPickupDelay(Duration.ofMillis(500))
                itemEntity.setInstance(instance, Pos(pos.x(), pos.y() + 0.5, pos.z()))

                itemEntity.scheduler().buildTask { itemEntity.remove() }
                    .delay(TaskSchedule.minutes(5))
                    .schedule()
            }
        }
    }
}
