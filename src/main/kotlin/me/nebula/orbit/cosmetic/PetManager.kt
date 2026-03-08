package me.nebula.orbit.cosmetic

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import me.nebula.orbit.utils.modelengine.modeledEntity
import me.nebula.orbit.utils.pathfinding.Pathfinder
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TELEPORT_THRESHOLD = 20.0
private const val FOLLOW_THRESHOLD = 3.0
private const val MOVE_SPEED = 0.15

data class ActivePet(
    val entity: EntityCreature,
    val modeled: ModeledEntity,
    val cosmeticId: String,
    val level: Int,
    var path: List<Vec>? = null,
    var pathIndex: Int = 0,
)

object PetManager {

    private val pets = ConcurrentHashMap<UUID, ActivePet>()
    private var task: Task? = null

    fun install() {
        task = MinecraftServer.getSchedulerManager()
            .buildTask { tick() }
            .repeat(TaskSchedule.tick(2))
            .schedule()
    }

    fun uninstall() {
        task?.cancel()
        task = null
        val iterator = pets.entries.iterator()
        while (iterator.hasNext()) {
            val (_, pet) = iterator.next()
            pet.modeled.destroy()
            pet.entity.remove()
            iterator.remove()
        }
    }

    fun spawn(player: Player, cosmeticId: String, level: Int) {
        despawn(player.uuid)
        val definition = CosmeticRegistry[cosmeticId] ?: return
        val resolved = definition.resolveData(level)
        val modelId = resolved["modelId"] ?: return
        if (ModelEngine.blueprintOrNull(modelId) == null) return
        val scale = resolved["scale"]?.toFloatOrNull() ?: 1.0f
        val instance = player.instance ?: return

        val creature = EntityCreature(EntityType.ZOMBIE)
        creature.isInvisible = true
        creature.isSilent = true
        creature.setInstance(instance, player.position.add(1.0, 0.0, 1.0)).thenRun {
            val modeled = modeledEntity(creature) {
                model(modelId, autoPlayIdle = true) { scale(scale) }
            }

            val walkAnim = resolved["walkAnimation"]
            modeled.show(player)

            pets[player.uuid] = ActivePet(creature, modeled, cosmeticId, level)
        }
    }

    fun despawn(playerId: UUID) {
        val pet = pets.remove(playerId) ?: return
        pet.modeled.destroy()
        pet.entity.remove()
    }

    fun isActive(playerId: UUID): Boolean = pets.containsKey(playerId)

    private fun tick() {
        val onlinePlayers = MinecraftServer.getConnectionManager().onlinePlayers
        val playersByUuid = onlinePlayers.associateBy { it.uuid }

        val iterator = pets.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, pet) = iterator.next()
            val player = playersByUuid[uuid]
            if (player == null) {
                pet.modeled.destroy()
                pet.entity.remove()
                iterator.remove()
                continue
            }

            if (pet.entity.instance != player.instance) {
                val inst = player.instance ?: continue
                pet.entity.setInstance(inst, player.position.add(1.0, 0.0, 1.0))
                pet.path = null
                pet.pathIndex = 0
                continue
            }

            val distance = pet.entity.position.distance(player.position)

            if (distance > TELEPORT_THRESHOLD) {
                pet.entity.teleport(player.position.add(1.0, 0.0, 1.0))
                pet.path = null
                pet.pathIndex = 0
                playIdleAnimation(pet)
            } else if (distance > FOLLOW_THRESHOLD) {
                followPlayer(pet, player)
            } else {
                pet.path = null
                pet.pathIndex = 0
                playIdleAnimation(pet)
            }

            for (nearby in player.instance?.players ?: emptyList()) {
                if (nearby.uuid == uuid) continue
                val inRange = nearby.position.distance(pet.entity.position) < 48.0
                val shouldShow = inRange && CosmeticVisibility.shouldShowModel(nearby, uuid)
                if (shouldShow && nearby.uuid !in pet.modeled.viewers) {
                    pet.modeled.show(nearby)
                } else if (!shouldShow && nearby.uuid in pet.modeled.viewers) {
                    pet.modeled.hide(nearby)
                }
            }
            if (player.uuid !in pet.modeled.viewers) {
                pet.modeled.show(player)
            }
        }
    }

    private fun followPlayer(pet: ActivePet, player: Player) {
        val instance = pet.entity.instance ?: return

        if (pet.path == null || pet.pathIndex >= (pet.path?.size ?: 0)) {
            pet.path = Pathfinder.findPath(instance, pet.entity.position, player.position, maxIterations = 200)
            pet.pathIndex = 0
            if (pet.path == null) {
                val direction = player.position.sub(pet.entity.position.x(), pet.entity.position.y(), pet.entity.position.z())
                val dist = direction.distance(Pos.ZERO)
                if (dist > 0.1) {
                    val normalized = Vec(direction.x() / dist, 0.0, direction.z() / dist)
                    pet.entity.velocity = normalized.mul(MOVE_SPEED * 20.0)
                }
                playWalkAnimation(pet)
                return
            }
        }

        val path = pet.path ?: return
        if (pet.pathIndex < path.size) {
            val target = path[pet.pathIndex]
            val current = pet.entity.position
            val dx = target.x() + 0.5 - current.x()
            val dz = target.z() + 0.5 - current.z()
            val horizontalDist = kotlin.math.sqrt(dx * dx + dz * dz)

            if (horizontalDist < 0.5) {
                pet.pathIndex++
            } else {
                val yaw = (-kotlin.math.atan2(dx, dz) * (180.0 / Math.PI)).toFloat()
                pet.entity.velocity = Vec(dx / horizontalDist * MOVE_SPEED * 20.0, 0.0, dz / horizontalDist * MOVE_SPEED * 20.0)
                pet.entity.setView(yaw, 0f)
            }
            playWalkAnimation(pet)
        }
    }

    private fun playWalkAnimation(pet: ActivePet) {
        val definition = CosmeticRegistry[pet.cosmeticId] ?: return
        val resolved = definition.resolveData(pet.level)
        val walkAnim = resolved["walkAnimation"] ?: return
        val model = pet.modeled.models.values.firstOrNull() ?: return
        if (!model.isPlayingAnimation(walkAnim)) {
            model.playAnimation(walkAnim, lerpIn = 0.2f, lerpOut = 0.2f, speed = 1.0f)
        }
    }

    private fun playIdleAnimation(pet: ActivePet) {
        val definition = CosmeticRegistry[pet.cosmeticId] ?: return
        val resolved = definition.resolveData(pet.level)
        val walkAnim = resolved["walkAnimation"]
        if (walkAnim != null) {
            val model = pet.modeled.models.values.firstOrNull() ?: return
            if (model.isPlayingAnimation(walkAnim)) {
                model.stopAnimation(walkAnim)
            }
        }
    }
}
