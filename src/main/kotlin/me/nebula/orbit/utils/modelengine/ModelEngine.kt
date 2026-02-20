package me.nebula.orbit.utils.modelengine

import me.nebula.orbit.utils.modelengine.blueprint.ModelBlueprint
import me.nebula.orbit.utils.modelengine.model.ModelOwner
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import me.nebula.orbit.utils.modelengine.model.ModeledEntityBuilder
import me.nebula.orbit.utils.modelengine.model.asModelOwner
import me.nebula.orbit.utils.modelengine.mount.MountManager
import me.nebula.orbit.utils.modelengine.vfx.VFXRegistry
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerDisconnectEvent
import net.minestom.server.timer.Task
import net.minestom.server.timer.TaskSchedule
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object ModelEngine {

    private val blueprints = ConcurrentHashMap<String, ModelBlueprint>()
    private val modeledEntities = ConcurrentHashMap<Int, ModeledEntity>()
    private var tickTask: Task? = null
    private var eventNode: EventNode<*>? = null

    fun registerBlueprint(name: String, blueprint: ModelBlueprint) {
        blueprints[name] = blueprint
    }

    fun unregisterBlueprint(name: String) {
        blueprints.remove(name)
    }

    fun blueprint(name: String): ModelBlueprint =
        requireNotNull(blueprints[name]) { "Blueprint '$name' not registered" }

    fun blueprintOrNull(name: String): ModelBlueprint? = blueprints[name]

    fun blueprints(): Map<String, ModelBlueprint> = blueprints.toMap()

    fun createModeledEntity(owner: ModelOwner): ModeledEntity {
        modeledEntities[owner.ownerId]?.destroy()
        val modeled = ModeledEntity(owner)
        modeledEntities[owner.ownerId] = modeled
        return modeled
    }

    fun createModeledEntity(entity: Entity): ModeledEntity =
        createModeledEntity(entity.asModelOwner())

    fun modeledEntity(owner: ModelOwner): ModeledEntity? =
        modeledEntities[owner.ownerId]

    fun modeledEntity(entity: Entity): ModeledEntity? =
        modeledEntities[entity.entityId]

    fun removeModeledEntity(owner: ModelOwner) {
        modeledEntities.remove(owner.ownerId)?.destroy()
    }

    fun removeModeledEntity(entity: Entity) {
        modeledEntities.remove(entity.entityId)?.destroy()
    }

    fun unregisterModeledEntity(owner: ModelOwner) {
        modeledEntities.remove(owner.ownerId)
    }

    fun unregisterModeledEntity(entity: Entity) {
        modeledEntities.remove(entity.entityId)
    }

    fun allModeledEntities(): Collection<ModeledEntity> = modeledEntities.values

    fun install() {
        require(tickTask == null) { "ModelEngine already installed" }

        tickTask = MinecraftServer.getSchedulerManager()
            .buildTask(::tick)
            .repeat(TaskSchedule.tick(1))
            .schedule()

        val node = EventNode.all("model-engine-sessions")
        node.addListener(PlayerDisconnectEvent::class.java) { event ->
            onPlayerDisconnect(event.player.uuid)
        }
        MinecraftServer.getGlobalEventHandler().addChild(node)
        eventNode = node
    }

    fun uninstall() {
        tickTask?.cancel()
        tickTask = null
        eventNode?.let { MinecraftServer.getGlobalEventHandler().removeChild(it) }
        eventNode = null
        modeledEntities.values.forEach { it.destroy() }
        modeledEntities.clear()
    }

    private fun onPlayerDisconnect(uuid: UUID) {
        modeledEntities.values.forEach { it.evictViewer(uuid) }
        MountManager.evictPlayer(uuid)
        VFXRegistry.evictPlayer(uuid)
    }

    private fun tick() {
        val iterator = modeledEntities.entries.iterator()
        while (iterator.hasNext()) {
            val (_, modeled) = iterator.next()
            if (modeled.owner.isRemoved) {
                modeled.destroy()
                iterator.remove()
                continue
            }
            modeled.tick()
        }
    }
}

inline fun modeledEntity(owner: ModelOwner, block: ModeledEntityBuilder.() -> Unit): ModeledEntity {
    val modeled = ModelEngine.createModeledEntity(owner)
    ModeledEntityBuilder(owner, modeled).apply(block)
    return modeled
}

inline fun modeledEntity(entity: Entity, block: ModeledEntityBuilder.() -> Unit): ModeledEntity =
    modeledEntity(entity.asModelOwner(), block)
