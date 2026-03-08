package me.nebula.orbit.cosmetic

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_DURATION_SECONDS = 10

data class ActiveGravestone(
    val owner: StandaloneModelOwner,
    val playerUuid: UUID,
    val expiresAt: Long,
)

object GravestoneManager {

    private val gravestones = ConcurrentHashMap<UUID, ActiveGravestone>()
    private var task: Task? = null

    fun install() {
        task = MinecraftServer.getSchedulerManager()
            .buildTask { tick() }
            .repeat(TaskSchedule.tick(20))
            .schedule()
    }

    fun uninstall() {
        task?.cancel()
        task = null
        val iterator = gravestones.entries.iterator()
        while (iterator.hasNext()) {
            val (_, gravestone) = iterator.next()
            gravestone.owner.remove()
            iterator.remove()
        }
    }

    fun spawn(instance: Instance, position: Pos, cosmeticId: String, level: Int, playerUuid: UUID) {
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val modelId = resolved["modelId"] ?: return
        if (ModelEngine.blueprintOrNull(modelId) == null) return
        val scale = resolved["scale"]?.toFloatOrNull() ?: 1.0f
        val duration = resolved["duration"]?.toIntOrNull() ?: DEFAULT_DURATION_SECONDS

        val model = standAloneModel(position) {
            model(modelId, autoPlayIdle = true) { scale(scale) }
        }

        val modeled = ModelEngine.modeledEntity(model)
        instance.players.forEach { player ->
            if (player.position.distance(position) < 48.0 && CosmeticVisibility.shouldShowModel(player, playerUuid)) {
                modeled?.show(player)
            }
        }

        val id = UUID.randomUUID()
        gravestones[id] = ActiveGravestone(model, playerUuid, System.currentTimeMillis() + duration * 1000L)
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val iterator = gravestones.entries.iterator()
        while (iterator.hasNext()) {
            val (_, gravestone) = iterator.next()
            if (now >= gravestone.expiresAt) {
                gravestone.owner.remove()
                iterator.remove()
                continue
            }

            val modeled = ModelEngine.modeledEntity(gravestone.owner) ?: continue
            for (instance in MinecraftServer.getInstanceManager().instances) {
                for (player in instance.players) {
                    val inRange = player.position.distance(gravestone.owner.position) < 48.0
                    val shouldShow = inRange && CosmeticVisibility.shouldShowModel(player, gravestone.playerUuid)
                    if (shouldShow && player.uuid !in modeled.viewers) {
                        modeled.show(player)
                    } else if (!shouldShow && player.uuid in modeled.viewers) {
                        modeled.hide(player)
                    }
                }
            }
        }
    }
}
