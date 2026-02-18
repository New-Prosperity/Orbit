package me.nebula.orbit.mechanic.trialspawner

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.BlockVec
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.ItemEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.entity.ai.target.LastEntityDamagerTarget
import net.minestom.server.instance.Instance
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private class TrialSpawnerState(
    val instance: Instance,
    val position: BlockVec,
    var lastActivation: Long = 0L,
    val spawnedMobs: MutableList<EntityCreature> = mutableListOf(),
    var rewardDropped: Boolean = false,
)

private const val ACTIVATION_RADIUS = 8
private const val COOLDOWN_MS = 1_800_000L
private const val MIN_MOBS = 3
private const val MAX_MOBS = 6

private val MOB_TYPES = listOf(
    EntityType.ZOMBIE,
    EntityType.SKELETON,
    EntityType.SPIDER,
    EntityType.HUSK,
    EntityType.STRAY,
)

private val REWARD_ITEMS = listOf(
    Material.DIAMOND,
    Material.EMERALD,
    Material.IRON_INGOT,
    Material.GOLD_INGOT,
    Material.GOLDEN_APPLE,
)

class TrialSpawnerModule : OrbitModule("trial-spawner") {

    private var tickTask: Task? = null
    private val activeSpawners = ConcurrentHashMap<BlockVec, TrialSpawnerState>()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.seconds(2))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        activeSpawners.values.forEach { state ->
            state.spawnedMobs.forEach { it.remove() }
        }
        activeSpawners.clear()
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            scanForTrialSpawners(player, instance)
        }

        activeSpawners.values.forEach { state ->
            checkMobsDefeated(state)
        }
    }

    private fun scanForTrialSpawners(player: Player, instance: Instance) {
        val px = player.position.blockX()
        val py = player.position.blockY()
        val pz = player.position.blockZ()

        for (x in (px - ACTIVATION_RADIUS)..(px + ACTIVATION_RADIUS)) {
            for (y in (py - ACTIVATION_RADIUS)..(py + ACTIVATION_RADIUS)) {
                for (z in (pz - ACTIVATION_RADIUS)..(pz + ACTIVATION_RADIUS)) {
                    val block = instance.getBlock(x, y, z)
                    if (block.name() != "minecraft:trial_spawner") continue

                    val blockVec = BlockVec(x, y, z)
                    val state = activeSpawners.getOrPut(blockVec) {
                        TrialSpawnerState(instance, blockVec)
                    }

                    val now = System.currentTimeMillis()
                    if (now - state.lastActivation < COOLDOWN_MS) continue
                    if (state.spawnedMobs.any { !it.isRemoved }) continue

                    activateSpawner(state)
                }
            }
        }
    }

    private fun activateSpawner(state: TrialSpawnerState) {
        state.lastActivation = System.currentTimeMillis()
        state.rewardDropped = false
        state.spawnedMobs.clear()

        val mobCount = Random.nextInt(MIN_MOBS, MAX_MOBS + 1)
        val pos = state.position

        repeat(mobCount) {
            val entityType = MOB_TYPES.random()
            val creature = EntityCreature(entityType)

            creature.addAIGroup(
                listOf(
                    MeleeAttackGoal(creature, 1.6, Duration.ofMillis(1000)),
                    RandomStrollGoal(creature, 5),
                ),
                listOf(
                    ClosestEntityTarget(creature, 32f, Player::class.java),
                    LastEntityDamagerTarget(creature, 16f),
                ),
            )

            val spawnX = pos.x() + 0.5 + Random.nextDouble(-2.0, 2.0)
            val spawnZ = pos.z() + 0.5 + Random.nextDouble(-2.0, 2.0)
            creature.setInstance(state.instance, Pos(spawnX, pos.y() + 1.0, spawnZ))
            state.spawnedMobs.add(creature)
        }
    }

    private fun checkMobsDefeated(state: TrialSpawnerState) {
        if (state.rewardDropped) return
        if (state.spawnedMobs.isEmpty()) return
        if (state.spawnedMobs.any { !it.isRemoved && !it.isDead }) return

        state.rewardDropped = true
        val pos = state.position
        val center = Pos(pos.x() + 0.5, pos.y() + 1.0, pos.z() + 0.5)

        val rewardMaterial = REWARD_ITEMS.random()
        val rewardCount = Random.nextInt(1, 4)
        val itemEntity = ItemEntity(ItemStack.of(rewardMaterial, rewardCount))
        itemEntity.setPickupDelay(Duration.ofMillis(500))
        itemEntity.setInstance(state.instance, center)

        itemEntity.scheduler().buildTask { itemEntity.remove() }
            .delay(TaskSchedule.minutes(5))
            .schedule()
    }
}
