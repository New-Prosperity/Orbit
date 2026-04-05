package me.nebula.orbit.cosmetic

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.coordinate.Pos
import net.minestom.server.instance.Instance
import net.minestom.server.timer.Task
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val DEFAULT_DURATION_SECONDS = 10

data class ActiveGravestone(
    val owner: StandaloneModelOwner,
    val playerUuid: UUID,
    val expiresAt: Long,
    val instance: Instance,
)

object GravestoneManager {

    private val gravestones = ConcurrentHashMap<UUID, ActiveGravestone>()
    private var task: Task? = null

    fun install() {
        task = repeat(20) { tick() }
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

        val modeled = ModelEngine.modeledEntity(model) ?: return
        CosmeticVisibility.updateViewers(
            modeled,
            instance.players,
            playerUuid,
            position,
            ensureOwnerVisible = false,
        )

        val id = UUID.randomUUID()
        gravestones[id] = ActiveGravestone(model, playerUuid, System.currentTimeMillis() + duration * 1000L, instance)
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

            val modeled = ModelEngine.modeledEntity(gravestone.owner) ?: run {
                gravestone.owner.remove()
                iterator.remove()
                continue
            }
            CosmeticVisibility.updateViewers(
                modeled,
                gravestone.instance.players,
                gravestone.playerUuid,
                gravestone.owner.position,
                ensureOwnerVisible = false,
            )
        }
    }
}
