package me.nebula.orbit.mechanic.parrotdance

import me.nebula.orbit.module.OrbitModule
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.animal.tameable.ParrotMeta
import net.minestom.server.instance.Instance
import net.minestom.server.tag.Tag
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.concurrent.ConcurrentHashMap

private val JUKEBOX_ACTIVE_TAG = Tag.Boolean("mechanic:parrot_dance:jukebox_active")
private val DANCING_TAG = Tag.Boolean("mechanic:parrot_dance:dancing").defaultValue(false)

private const val DANCE_RANGE = 3.0

class ParrotDanceModule : OrbitModule("parrot-dance") {

    private var tickTask: Task? = null
    private val activeJukeboxes = ConcurrentHashMap.newKeySet<JukeboxKey>()

    private data class JukeboxKey(val instanceHash: Int, val x: Int, val y: Int, val z: Int)

    override fun onEnable() {
        super.onEnable()
        activeJukeboxes.cleanOnInstanceRemove { it.instanceHash }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null
        activeJukeboxes.clear()
        super.onDisable()
    }

    fun startJukebox(instance: Instance, pos: Point) {
        activeJukeboxes.add(JukeboxKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
    }

    fun stopJukebox(instance: Instance, pos: Point) {
        activeJukeboxes.remove(JukeboxKey(System.identityHashCode(instance), pos.blockX(), pos.blockY(), pos.blockZ()))
    }

    private fun tick() {
        MinecraftServer.getConnectionManager().onlinePlayers.forEach { player ->
            val instance = player.instance ?: return@forEach

            instance.entities.forEach entityLoop@{ entity ->
                if (entity.entityType != EntityType.PARROT) return@entityLoop
                val meta = entity.entityMeta as? ParrotMeta ?: return@entityLoop

                val nearJukebox = isNearActiveJukebox(entity, instance)
                val isDancing = entity.getTag(DANCING_TAG)

                if (nearJukebox && !isDancing) {
                    entity.setTag(DANCING_TAG, true)
                } else if (!nearJukebox && isDancing) {
                    entity.setTag(DANCING_TAG, false)
                }
            }
        }
    }

    private fun isNearActiveJukebox(entity: Entity, instance: Instance): Boolean {
        val instHash = System.identityHashCode(instance)
        return activeJukeboxes.any { jukebox ->
            jukebox.instanceHash == instHash &&
                entity.position.distance(
                    net.minestom.server.coordinate.Vec(
                        jukebox.x + 0.5,
                        jukebox.y + 0.5,
                        jukebox.z + 0.5,
                    ),
                ) <= DANCE_RANGE
        }
    }
}
