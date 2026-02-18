package me.nebula.orbit.mechanic.raidsystem

import me.nebula.orbit.module.OrbitModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.instance.Instance
import net.minestom.server.potion.PotionEffect
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private const val BELL_SEARCH_RANGE = 48
private const val SCAN_INTERVAL_TICKS = 40
private const val MAX_WAVES = 5
private const val SPAWN_RANGE = 20

private data class RaidKey(val instanceHash: Int, val bellX: Int, val bellY: Int, val bellZ: Int)

private data class RaidState(
    var currentWave: Int = 0,
    var activeEntities: MutableSet<EntityCreature> = ConcurrentHashMap.newKeySet(),
    var isActive: Boolean = true,
)

class RaidSystemModule : OrbitModule("raid-system") {

    private var tickTask: Task? = null
    private val activeRaids = ConcurrentHashMap<RaidKey, RaidState>()

    override fun onEnable() {
        super.onEnable()
        activeRaids.cleanOnInstanceRemove { it.instanceHash }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        activeRaids.values.forEach { raid ->
            raid.activeEntities.forEach { it.remove() }
        }
        activeRaids.clear()
        super.onDisable()
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            if (!hasBadOmen(player)) return@forEach
            val instance = player.instance ?: return@forEach
            val bellPos = findNearbyBell(player, instance) ?: return@forEach
            val key = RaidKey(System.identityHashCode(instance), bellPos[0], bellPos[1], bellPos[2])

            if (activeRaids.containsKey(key)) return@forEach

            player.removeEffect(PotionEffect.BAD_OMEN)
            startRaid(key, instance, Pos(bellPos[0].toDouble(), bellPos[1].toDouble(), bellPos[2].toDouble()))
        }

        activeRaids.entries.removeIf { (_, raid) -> !raid.isActive }

        activeRaids.forEach { (key, raid) ->
            raid.activeEntities.removeIf { it.isRemoved || it.isDead }

            if (raid.activeEntities.isEmpty() && raid.isActive) {
                if (raid.currentWave < MAX_WAVES) {
                    raid.currentWave++
                    spawnWave(key, raid)
                } else {
                    raid.isActive = false
                }
            }
        }
    }

    private fun hasBadOmen(player: Player): Boolean =
        player.activeEffects.any { it.potion().effect() == PotionEffect.BAD_OMEN }

    private fun findNearbyBell(player: Player, instance: Instance): IntArray? {
        val pos = player.position
        val range = BELL_SEARCH_RANGE.coerceAtMost(24)
        for (dx in -range..range) {
            for (dy in -8..8) {
                for (dz in -range..range) {
                    val bx = pos.blockX() + dx
                    val by = pos.blockY() + dy
                    val bz = pos.blockZ() + dz
                    if (instance.getBlock(bx, by, bz).name() == "minecraft:bell") {
                        return intArrayOf(bx, by, bz)
                    }
                }
            }
        }
        return null
    }

    private fun startRaid(key: RaidKey, instance: Instance, bellPos: Pos) {
        val raid = RaidState(currentWave = 1)
        activeRaids[key] = raid
        spawnWave(key, raid)

        instance.playSound(
            Sound.sound(SoundEvent.EVENT_RAID_HORN.key(), Sound.Source.HOSTILE, 2f, 1f),
            bellPos.x(), bellPos.y(), bellPos.z(),
        )
    }

    private fun spawnWave(key: RaidKey, raid: RaidState) {
        val instance = MinecraftServer.getConnectionManager().onlinePlayers
            .mapNotNull { it.instance }
            .firstOrNull { System.identityHashCode(it) == key.instanceHash } ?: return

        val center = Pos(key.bellX.toDouble(), key.bellY.toDouble(), key.bellZ.toDouble())
        val pillagerCount = raid.currentWave + 2
        val vindicatorCount = (raid.currentWave - 1).coerceAtLeast(0)

        repeat(pillagerCount) { spawnRaidMob(instance, center, EntityType.PILLAGER, raid) }
        repeat(vindicatorCount) { spawnRaidMob(instance, center, EntityType.VINDICATOR, raid) }
    }

    private fun spawnRaidMob(instance: Instance, center: Pos, type: EntityType, raid: RaidState) {
        val creature = EntityCreature(type)
        creature.addAIGroup(
            listOf(
                MeleeAttackGoal(creature, 1.8, Duration.ofMillis(800)),
                RandomStrollGoal(creature, 8),
            ),
            listOf(
                ClosestEntityTarget(creature, 32f, Player::class.java),
            ),
        )

        val spawnPos = Pos(
            center.x() + Random.nextDouble(-SPAWN_RANGE.toDouble(), SPAWN_RANGE.toDouble()),
            center.y(),
            center.z() + Random.nextDouble(-SPAWN_RANGE.toDouble(), SPAWN_RANGE.toDouble()),
        )
        creature.setInstance(instance, spawnPos)
        raid.activeEntities.add(creature)
    }
}
