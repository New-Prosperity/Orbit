package me.nebula.orbit.cosmetic

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import me.nebula.orbit.utils.modelengine.modeledEntity
import me.nebula.orbit.utils.scheduler.repeat
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.Task
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
)

object PetManager {

    private val pets = ConcurrentHashMap<UUID, ActivePet>()
    private var task: Task? = null
    private var eventNode: EventNode<*>? = null

    fun install() {
        task = repeat(2) { tick() }
        val node = EventNode.all("pet-manager")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            despawn(event.player.uuid)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        task?.cancel()
        task = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
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
        creature.getAttribute(Attribute.MOVEMENT_SPEED).baseValue = MOVE_SPEED
        creature.setInstance(instance, player.position.add(1.0, 0.0, 1.0)).thenRun {
            if (creature.isRemoved) return@thenRun

            val modeled = modeledEntity(creature) {
                model(modelId, autoPlayIdle = true) { scale(scale) }
            }

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
        val connectionManager = MinecraftServer.getConnectionManager()

        val iterator = pets.entries.iterator()
        while (iterator.hasNext()) {
            val (uuid, pet) = iterator.next()
            val player = connectionManager.getOnlinePlayerByUuid(uuid)
            if (player == null) {
                pet.modeled.destroy()
                pet.entity.remove()
                iterator.remove()
                continue
            }

            if (pet.entity.instance != player.instance) {
                val inst = player.instance ?: continue
                pet.entity.setInstance(inst, player.position.add(1.0, 0.0, 1.0))
                continue
            }

            val navigator = pet.entity.navigator
            val distance = pet.entity.position.distance(player.position)

            if (distance > TELEPORT_THRESHOLD) {
                navigator.reset()
                pet.entity.teleport(player.position.add(1.0, 0.0, 1.0))
                playIdleAnimation(pet)
            } else if (distance > FOLLOW_THRESHOLD) {
                navigator.setPathTo(player.position, FOLLOW_THRESHOLD, 50.0, 20.0, null)
                playWalkAnimation(pet)
            } else {
                navigator.reset()
                playIdleAnimation(pet)
            }

            CosmeticVisibility.updateViewers(
                pet.modeled,
                player.instance?.players ?: emptyList(),
                uuid,
                pet.entity.position,
            )
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
