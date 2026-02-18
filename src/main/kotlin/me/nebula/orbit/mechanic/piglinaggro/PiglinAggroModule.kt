package me.nebula.orbit.mechanic.piglinaggro

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.ai.goal.MeleeAttackGoal
import net.minestom.server.entity.ai.target.ClosestEntityTarget
import net.minestom.server.item.Material
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val AGGRO_CONFIGURED_TAG = Tag.Boolean("mechanic:piglin_aggro:configured").defaultValue(false)

private const val PROXIMITY_RANGE = 10.0
private const val SCAN_INTERVAL_TICKS = 20

private val GOLD_ARMOR = setOf(
    Material.GOLDEN_HELMET,
    Material.GOLDEN_CHESTPLATE,
    Material.GOLDEN_LEGGINGS,
    Material.GOLDEN_BOOTS,
)

class PiglinAggroModule : OrbitModule("piglin-aggro") {

    private var tickTask: Task? = null
    private val trackedPiglins: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedPiglins.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedPiglins.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.PIGLIN) return@entityLoop
                trackedPiglins.add(entity)
            }
        }

        trackedPiglins.forEach { piglin ->
            if (piglin.isRemoved) return@forEach
            if (piglin !is EntityCreature) return@forEach
            val instance = piglin.instance ?: return@forEach

            val nearestPlayer = findNearestPlayer(piglin, instance) ?: return@forEach
            if (isWearingGold(nearestPlayer)) return@forEach

            if (!piglin.getTag(AGGRO_CONFIGURED_TAG)) {
                piglin.addAIGroup(
                    listOf(MeleeAttackGoal(piglin, 1.8, Duration.ofMillis(800))),
                    listOf(ClosestEntityTarget(piglin, PROXIMITY_RANGE.toFloat(), Player::class.java)),
                )
                piglin.setTag(AGGRO_CONFIGURED_TAG, true)
            }
        }
    }

    private fun isWearingGold(player: Player): Boolean {
        val helmet = player.inventory.getItemStack(5).material()
        val chestplate = player.inventory.getItemStack(6).material()
        val leggings = player.inventory.getItemStack(7).material()
        val boots = player.inventory.getItemStack(8).material()
        return helmet in GOLD_ARMOR || chestplate in GOLD_ARMOR || leggings in GOLD_ARMOR || boots in GOLD_ARMOR
    }

    private fun findNearestPlayer(piglin: Entity, instance: net.minestom.server.instance.Instance): Player? {
        var nearest: Player? = null
        var nearestDist = PROXIMITY_RANGE * PROXIMITY_RANGE

        instance.players.forEach { player ->
            if (player.isDead) return@forEach
            val dist = piglin.position.distanceSquared(player.position)
            if (dist < nearestDist) {
                nearestDist = dist
                nearest = player
            }
        }
        return nearest
    }
}
