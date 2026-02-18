package me.nebula.orbit.mechanic.zombiesiege

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
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val SIEGE_ACTIVE_TAG = Tag.Boolean("mechanic:zombie_siege:active").defaultValue(false)

private const val SCAN_INTERVAL_TICKS = 100
private const val BED_SEARCH_RANGE = 100
private const val MIN_BEDS = 20
private const val MAX_ZOMBIES_PER_SIEGE = 20
private const val SPAWN_RANGE = 20
private const val TIME_START = 18000L
private const val TIME_END = 23000L

class ZombieSiegeModule : OrbitModule("zombie-siege") {

    private var tickTask: Task? = null
    private val siegeZombies: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
    private val siegeInstances: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()
        siegeInstances.cleanOnInstanceRemove { it }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        siegeZombies.forEach { it.remove() }
        siegeZombies.clear()
        siegeInstances.clear()
        super.onDisable()
    }

    private fun tick() {
        siegeZombies.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            val instanceHash = System.identityHashCode(instance)
            val time = instance.time % 24000

            if (time !in TIME_START..TIME_END) {
                siegeInstances.remove(instanceHash)
                return@forEach
            }

            if (instanceHash in siegeInstances) return@forEach

            val bellPos = findNearbyBell(player, instance) ?: return@forEach
            val bellCenter = Pos(bellPos[0].toDouble(), bellPos[1].toDouble(), bellPos[2].toDouble())

            val bedCount = countNearbyBeds(instance, bellCenter)
            if (bedCount < MIN_BEDS) return@forEach

            siegeInstances.add(instanceHash)
            spawnSiegeZombies(instance, bellCenter)
        }
    }

    private fun findNearbyBell(player: Player, instance: Instance): IntArray? {
        val pos = player.position
        val range = 48
        for (dx in -range..range step 4) {
            for (dy in -8..8) {
                for (dz in -range..range step 4) {
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

    private fun countNearbyBeds(instance: Instance, center: Pos): Int {
        var count = 0
        val range = BED_SEARCH_RANGE.coerceAtMost(50)
        for (dx in -range..range step 2) {
            for (dy in -4..4) {
                for (dz in -range..range step 2) {
                    val block = instance.getBlock(center.blockX() + dx, center.blockY() + dy, center.blockZ() + dz)
                    if (block.name().contains("bed")) count++
                }
            }
        }
        return count
    }

    private fun spawnSiegeZombies(instance: Instance, center: Pos) {
        val currentSiegeCount = siegeZombies.count { !it.isRemoved }
        val toSpawn = (MAX_ZOMBIES_PER_SIEGE - currentSiegeCount).coerceIn(0, MAX_ZOMBIES_PER_SIEGE)

        repeat(toSpawn) {
            val zombie = EntityCreature(EntityType.ZOMBIE)
            zombie.addAIGroup(
                listOf(
                    MeleeAttackGoal(zombie, 1.6, Duration.ofMillis(1000)),
                    RandomStrollGoal(zombie, 5),
                ),
                listOf(
                    ClosestEntityTarget(zombie, 32f, Player::class.java),
                ),
            )

            val spawnPos = Pos(
                center.x() + Random.nextDouble(-SPAWN_RANGE.toDouble(), SPAWN_RANGE.toDouble()),
                center.y(),
                center.z() + Random.nextDouble(-SPAWN_RANGE.toDouble(), SPAWN_RANGE.toDouble()),
            )
            zombie.setInstance(instance, spawnPos)
            siegeZombies.add(zombie)
        }
    }
}
