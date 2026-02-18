package me.nebula.orbit.mechanic.drownedconversion

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
import net.minestom.server.entity.ai.target.LastEntityDamagerTarget
import net.minestom.server.instance.block.Block
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

private val SUBMERGE_TICKS_TAG = Tag.Integer("mechanic:drowned_conversion:submerge_ticks").defaultValue(0)

private const val CONVERSION_THRESHOLD_TICKS = 600
private const val SCAN_INTERVAL_TICKS = 20

class DrownedConversionModule : OrbitModule("drowned-conversion") {

    private var tickTask: Task? = null
    private val trackedEntities: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

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
        trackedEntities.clear()
        super.onDisable()
    }

    private fun tick() {
        trackedEntities.removeIf { it.isRemoved }

        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach
            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.ZOMBIE) return@entityLoop
                if (entity in trackedEntities) return@entityLoop

                trackedEntities.add(entity)
            }
        }

        trackedEntities.forEach { entity ->
            if (entity.isRemoved || entity.entityType != EntityType.ZOMBIE) return@forEach
            val instance = entity.instance ?: return@forEach
            val headY = (entity.position.y() + 1.8).toInt()
            val headBlock = instance.getBlock(entity.position.blockX(), headY, entity.position.blockZ())

            if (headBlock == Block.WATER) {
                val ticks = entity.getTag(SUBMERGE_TICKS_TAG) + SCAN_INTERVAL_TICKS
                entity.setTag(SUBMERGE_TICKS_TAG, ticks)

                if (ticks >= CONVERSION_THRESHOLD_TICKS) {
                    convertToDrowned(entity)
                }
            } else {
                entity.setTag(SUBMERGE_TICKS_TAG, 0)
            }
        }
    }

    private fun convertToDrowned(zombie: Entity) {
        val instance = zombie.instance ?: return
        val pos = zombie.position

        zombie.remove()
        trackedEntities.remove(zombie)

        val drowned = EntityCreature(EntityType.DROWNED)
        drowned.addAIGroup(
            listOf(
                MeleeAttackGoal(drowned, 1.4, Duration.ofMillis(1000)),
                RandomStrollGoal(drowned, 5),
            ),
            listOf(
                ClosestEntityTarget(drowned, 16f, Player::class.java),
                LastEntityDamagerTarget(drowned, 16f),
            ),
        )
        drowned.setInstance(instance, Pos(pos.x(), pos.y(), pos.z()))
        trackedEntities.add(drowned)
    }
}
