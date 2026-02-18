package me.nebula.orbit.mechanic.wanderingtrader

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.RandomStrollGoal
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

private val SPAWN_TIME_TAG = Tag.Long("mechanic:wandering_trader:spawn_time").defaultValue(0L)
private val OWNER_UUID_TAG = Tag.String("mechanic:wandering_trader:owner")

private const val SPAWN_INTERVAL_TICKS = 24000
private const val DESPAWN_LIFETIME_TICKS = 48000
private const val SPAWN_RANGE = 16
private const val SCAN_INTERVAL_TICKS = 100

class WanderingTraderModule : OrbitModule("wandering-trader") {

    private var tickTask: Task? = null
    private var tickCounter = 0L
    private val activeTraders: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(SCAN_INTERVAL_TICKS))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        activeTraders.forEach { it.remove() }
        activeTraders.clear()
        super.onDisable()
    }

    private fun tick() {
        tickCounter += SCAN_INTERVAL_TICKS
        activeTraders.removeIf { it.isRemoved }

        val now = System.currentTimeMillis()
        activeTraders.toList().forEach { trader ->
            val spawnTime = trader.getTag(SPAWN_TIME_TAG)
            val aliveTicks = ((now - spawnTime) / 50)
            if (aliveTicks >= DESPAWN_LIFETIME_TICKS) {
                trader.remove()
                activeTraders.remove(trader)
            }
        }

        if (tickCounter % SPAWN_INTERVAL_TICKS < SCAN_INTERVAL_TICKS) {
            MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
                val hasTrader = activeTraders.any {
                    !it.isRemoved && it.getTag(OWNER_UUID_TAG) == player.uuid.toString()
                }
                if (!hasTrader) spawnTrader(player)
            }
        }
    }

    private fun spawnTrader(player: Player) {
        val instance = player.instance ?: return
        val pos = player.position

        val spawnPos = Pos(
            pos.x() + Random.nextDouble(-SPAWN_RANGE.toDouble(), SPAWN_RANGE.toDouble()),
            pos.y(),
            pos.z() + Random.nextDouble(-SPAWN_RANGE.toDouble(), SPAWN_RANGE.toDouble()),
        )

        val now = System.currentTimeMillis()

        val trader = EntityCreature(EntityType.WANDERING_TRADER)
        trader.setTag(SPAWN_TIME_TAG, now)
        trader.setTag(OWNER_UUID_TAG, player.uuid.toString())
        trader.addAIGroup(listOf(RandomStrollGoal(trader, 10)), emptyList())
        trader.setInstance(instance, spawnPos)
        activeTraders.add(trader)

        repeat(2) {
            val llama = EntityCreature(EntityType.TRADER_LLAMA)
            llama.setTag(SPAWN_TIME_TAG, now)
            llama.addAIGroup(listOf(RandomStrollGoal(llama, 5)), emptyList())
            llama.setInstance(instance, Pos(
                spawnPos.x() + Random.nextDouble(-2.0, 2.0),
                spawnPos.y(),
                spawnPos.z() + Random.nextDouble(-2.0, 2.0),
            ))
            activeTraders.add(llama)
        }
    }
}
