package me.nebula.orbit.utils.blockhighlight

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class HighlightKey(val playerUuid: UUID, val x: Int, val y: Int, val z: Int)

object BlockHighlightManager {

    private val highlights = ConcurrentHashMap<HighlightKey, Entity>()

    fun highlight(player: Player, x: Int, y: Int, z: Int, durationTicks: Int = 100): Entity {
        val key = HighlightKey(player.uuid, x, y, z)
        remove(key)

        val instance = player.instance ?: error("Player has no instance")
        val entity = Entity(EntityType.SHULKER)
        entity.isInvisible = true
        entity.setNoGravity(true)
        entity.isGlowing = true
        entity.setInstance(instance, Vec(x + 0.5, y.toDouble(), z + 0.5))

        highlights[key] = entity

        if (durationTicks > 0) {
            MinecraftServer.getSchedulerManager().buildTask {
                remove(key)
            }.delay(TaskSchedule.tick(durationTicks)).schedule()
        }

        return entity
    }

    fun remove(key: HighlightKey) {
        highlights.remove(key)?.let { if (!it.isRemoved) it.remove() }
    }

    fun removeAll(player: Player) {
        highlights.keys.filter { it.playerUuid == player.uuid }.forEach { remove(it) }
    }

    fun clear() {
        highlights.values.forEach { if (!it.isRemoved) it.remove() }
        highlights.clear()
    }
}

fun Player.highlightBlock(x: Int, y: Int, z: Int, durationTicks: Int = 100): Entity =
    BlockHighlightManager.highlight(this, x, y, z, durationTicks)

fun Player.clearHighlights() = BlockHighlightManager.removeAll(this)
