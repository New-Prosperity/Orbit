package me.nebula.orbit.utils.customcontent.furniture

import me.nebula.ether.utils.logging.logger
import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.math.eulerToQuat
import me.nebula.orbit.utils.modelengine.model.StandaloneModelOwner
import me.nebula.orbit.utils.modelengine.model.standAloneModel
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.concurrent.ConcurrentHashMap

object FurnitureDisplaySpawner {

    private val logger = logger("FurnitureDisplaySpawner")
    private val owners = ConcurrentHashMap<Int, StandaloneModelOwner>()

    fun spawn(
        definition: FurnitureDefinition,
        instance: Instance,
        anchorX: Int,
        anchorY: Int,
        anchorZ: Int,
        yawDegrees: Float,
        pitchDegrees: Float = 0f,
        rollDegrees: Float = 0f,
    ): SpawnedDisplay {
        val blueprintName = definition.id
        require(ModelEngine.blueprintOrNull(blueprintName) != null) {
            "Blueprint '$blueprintName' not registered — did boot-time registration run for this furniture?"
        }
        val centerPos = Pos(anchorX + 0.5, anchorY.toDouble(), anchorZ + 0.5, 0f, 0f)
        val owner = standAloneModel(centerPos) {
            model(blueprintName, autoPlayIdle = true) {
                scale(definition.scale.toFloat())
            }
        }
        owner.modeledEntity?.models?.values?.forEach { active ->
            active.setRootPlacementRotation(eulerToQuat(pitchDegrees, yawDegrees, rollDegrees))
        }
        owners[owner.ownerId] = owner
        for (player in instance.players) {
            owner.show(player)
        }
        return SpawnedDisplay(owner.ownerId, owner)
    }

    fun despawn(instance: Instance, entityId: Int) {
        val owner = owners.remove(entityId) ?: return
        owner.remove()
    }

    fun showAllTo(player: Player) {
        owners.values.forEach { owner ->
            runCatching { owner.show(player) }.onFailure {
                logger.warn { "Failed to show furniture to ${player.username}: ${it.message}" }
            }
        }
    }

    fun setItem(instance: Instance, entityId: Int, item: net.minestom.server.item.ItemStack) {
        logger.warn { "setItem() is a no-op under ModelEngine-backed rendering; OpenClose variant swaps need separate blueprints — not yet implemented." }
    }

    fun setRotation(instance: Instance, entityId: Int, yawDegrees: Float, pitchDegrees: Float = 0f, rollDegrees: Float = 0f) {
        val owner = owners[entityId] ?: return
        owner.modeledEntity?.models?.values?.forEach { active ->
            active.setRootPlacementRotation(eulerToQuat(pitchDegrees, yawDegrees, rollDegrees))
        }
    }

    fun moveAnchor(instance: Instance, entityId: Int, anchorX: Int, anchorY: Int, anchorZ: Int) {
        val owner = owners[entityId] ?: return
        owner.position = Pos(anchorX + 0.5, anchorY.toDouble(), anchorZ + 0.5, owner.position.yaw(), owner.position.pitch())
    }

    internal fun clearAll() {
        owners.values.forEach { it.remove() }
        owners.clear()
    }

    data class SpawnedDisplay(val entityId: Int, val owner: StandaloneModelOwner) {
        fun remove() {
            owners.remove(entityId)
            owner.remove()
        }
    }
}
