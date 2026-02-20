package me.nebula.orbit.utils.modelengine.model

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.blueprint.ModelBlueprint
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ModeledEntity(val owner: ModelOwner) {

    val entityOrNull: Entity? get() = (owner as? EntityModelOwner)?.entity

    private val _models = ConcurrentHashMap<String, ActiveModel>()
    val models: Map<String, ActiveModel> get() = _models

    private val _viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val viewers: Set<UUID> get() = _viewers

    var headYaw: Float = 0f
    var headPitch: Float = 0f

    fun addModel(id: String, blueprint: ModelBlueprint): ActiveModel {
        val model = ActiveModel(blueprint)
        _models[id] = model
        _viewers.forEach { uuid ->
            findPlayer(uuid)?.let { model.show(it, owner.position) }
        }
        model.initBehaviors(this)
        return model
    }

    fun removeModel(id: String): ActiveModel? {
        val model = _models.remove(id) ?: return null
        model.destroyBehaviors(this)
        model.destroy()
        return model
    }

    fun model(id: String): ActiveModel? = _models[id]

    fun show(player: Player) {
        if (!_viewers.add(player.uuid)) return
        _models.values.forEach { it.show(player, owner.position) }
    }

    fun hide(player: Player) {
        if (!_viewers.remove(player.uuid)) return
        _models.values.forEach { it.hide(player) }
    }

    fun evictViewer(uuid: UUID) {
        _viewers.remove(uuid)
        _models.values.forEach { model ->
            model.renderer.evictViewer(uuid)
            model.evictBehaviorViewer(uuid)
        }
    }

    fun tick() {
        _models.values.forEach { model ->
            model.computeTransforms()
            model.tickBehaviors(this)
            model.updateRenderer(owner.position)
        }
    }

    fun destroy() {
        _models.values.forEach {
            it.destroyBehaviors(this)
            it.destroy()
        }
        _models.clear()
        _viewers.clear()
        ModelEngine.unregisterModeledEntity(owner)
    }

    private fun findPlayer(uuid: UUID): Player? =
        net.minestom.server.MinecraftServer.getConnectionManager()
            .onlinePlayers.firstOrNull { it.uuid == uuid }
}

class ModeledEntityBuilder @PublishedApi internal constructor(
    private val owner: ModelOwner,
    private val modeledEntity: ModeledEntity,
) {
    fun model(id: String, block: ActiveModelBuilder.() -> Unit = {}) {
        val blueprint = ModelEngine.blueprint(id)
        val model = modeledEntity.addModel(id, blueprint)
        ActiveModelBuilder(model).apply(block)
    }
}

class ActiveModelBuilder @PublishedApi internal constructor(private val model: ActiveModel) {
    fun scale(scale: Float) { model.modelScale = scale }
}
