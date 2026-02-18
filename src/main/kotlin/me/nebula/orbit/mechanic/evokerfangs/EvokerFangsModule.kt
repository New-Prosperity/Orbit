package me.nebula.orbit.mechanic.evokerfangs

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.damage.DamageType
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val LAST_FANG_TAG = Tag.Long("mechanic:evoker_fangs:last_fang").defaultValue(0L)

private const val SEARCH_RANGE = 16.0
private const val FANG_COOLDOWN_MS = 5000L
private const val FANG_DAMAGE = 6f
private const val FANG_HIT_RANGE = 1.5
private const val FANG_COUNT = 16

class EvokerFangsModule : OrbitModule("evoker-fangs") {

    private var tickTask: Task? = null
    private val trackedEvokers: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

    override fun onEnable() {
        super.onEnable()

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        trackedEvokers.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedEvokers.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.EVOKER) return@entityLoop
                trackedEvokers.add(entity)
            }
        }

        val now = System.currentTimeMillis()
        trackedEvokers.forEach { evoker ->
            if (evoker.isRemoved) return@forEach
            val lastFang = evoker.getTag(LAST_FANG_TAG)
            if (now - lastFang < FANG_COOLDOWN_MS) return@forEach

            val instance = evoker.instance ?: return@forEach
            val target = findNearestPlayer(evoker, instance) ?: return@forEach

            evoker.setTag(LAST_FANG_TAG, now)
            spawnFangLine(evoker, target, instance)
        }
    }

    private fun spawnFangLine(evoker: Entity, target: Player, instance: Instance) {
        val direction = target.position.asVec()
            .sub(evoker.position.asVec())
            .normalize()

        for (i in 1..FANG_COUNT) {
            val fangPos = evoker.position.add(
                direction.x() * i,
                0.0,
                direction.z() * i,
            )

            val delay = (i * 2).toLong()
            MinecraftServer.getSchedulerManager().buildTask {
                instance.getNearbyEntities(fangPos, FANG_HIT_RANGE).forEach { entity ->
                    if (entity is Player && !entity.isDead) {
                        entity.damage(DamageType.MAGIC, FANG_DAMAGE)
                    }
                }
            }.delay(TaskSchedule.tick(delay.toInt())).schedule()
        }
    }

    private fun findNearestPlayer(evoker: Entity, instance: Instance): Player? {
        var nearest: Player? = null
        var nearestDist = SEARCH_RANGE * SEARCH_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = evoker.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
