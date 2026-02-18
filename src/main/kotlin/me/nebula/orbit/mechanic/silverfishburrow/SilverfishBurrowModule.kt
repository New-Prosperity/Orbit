package me.nebula.orbit.mechanic.silverfishburrow

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.event.player.PlayerBlockBreakEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val LAST_BURROW_TAG = Tag.Long("mechanic:silverfish_burrow:last_burrow").defaultValue(0L)

private const val BURROW_COOLDOWN_MS = 10000L

private val INFESTABLE_MAP = mapOf(
    Block.STONE to Block.INFESTED_STONE,
    Block.COBBLESTONE to Block.INFESTED_COBBLESTONE,
    Block.STONE_BRICKS to Block.INFESTED_STONE_BRICKS,
    Block.MOSSY_STONE_BRICKS to Block.INFESTED_MOSSY_STONE_BRICKS,
    Block.CRACKED_STONE_BRICKS to Block.INFESTED_CRACKED_STONE_BRICKS,
    Block.CHISELED_STONE_BRICKS to Block.INFESTED_CHISELED_STONE_BRICKS,
)

private val INFESTED_BLOCKS = setOf(
    Block.INFESTED_STONE, Block.INFESTED_COBBLESTONE, Block.INFESTED_STONE_BRICKS,
    Block.INFESTED_MOSSY_STONE_BRICKS, Block.INFESTED_CRACKED_STONE_BRICKS,
    Block.INFESTED_CHISELED_STONE_BRICKS,
)

class SilverfishBurrowModule : OrbitModule("silverfish-burrow") {

    private var tickTask: Task? = null
    private val trackedSilverfish: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        eventNode.addListener(PlayerBlockBreakEvent::class.java) { event ->
            val block = event.block
            if (block !in INFESTED_BLOCKS) return@addListener

            val instance = event.player.instance ?: return@addListener
            val pos = event.blockPosition
            spawnSilverfish(instance, Pos(pos.x() + 0.5, pos.y(), pos.z() + 0.5))
        }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(40))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedSilverfish.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedSilverfish.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.SILVERFISH) return@entityLoop
                trackedSilverfish.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedSilverfish.toList().forEach { silverfish ->
            if (silverfish.isRemoved) return@forEach
            val lastBurrow = silverfish.getTag(LAST_BURROW_TAG)
            if (now - lastBurrow < BURROW_COOLDOWN_MS) return@forEach
            if (Random.nextInt(5) != 0) return@forEach

            val instance = silverfish.instance ?: return@forEach
            val nearPlayer = instance.players.any {
                !it.isDead && silverfish.position.distanceSquared(it.position) < 64.0
            }
            if (nearPlayer) return@forEach

            tryBurrow(silverfish, instance, now)
        }
    }

    private fun tryBurrow(silverfish: Entity, instance: Instance, now: Long) {
        val pos = silverfish.position
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    val bx = pos.blockX() + dx
                    val by = pos.blockY() + dy
                    val bz = pos.blockZ() + dz
                    val block = instance.getBlock(bx, by, bz)
                    val infested = INFESTABLE_MAP[block]
                    if (infested != null) {
                        instance.setBlock(bx, by, bz, infested)
                        silverfish.remove()
                        trackedSilverfish.remove(silverfish)
                        return
                    }
                }
            }
        }
        silverfish.setTag(LAST_BURROW_TAG, now)
    }

    private fun spawnSilverfish(instance: Instance, pos: Pos) {
        val creature = EntityCreature(EntityType.SILVERFISH)
        creature.addAIGroup(
            listOf(
                MeleeAttackGoal(creature, 1.4, Duration.ofMillis(800)),
                RandomStrollGoal(creature, 5),
            ),
            listOf(
                ClosestEntityTarget(creature, 16f, Player::class.java),
            ),
        )
        creature.setInstance(instance, pos)
        trackedSilverfish.add(creature)
    }
}
