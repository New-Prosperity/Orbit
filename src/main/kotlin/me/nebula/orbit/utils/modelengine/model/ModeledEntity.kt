package me.nebula.orbit.utils.modelengine.model

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.behavior.MountBehavior
import me.nebula.orbit.utils.modelengine.blueprint.ModelBlueprint
import me.nebula.orbit.utils.modelengine.hitbox.ModelHitbox
import me.nebula.orbit.utils.modelengine.mount.PassiveMountController
import me.nebula.orbit.utils.modelengine.mount.SeatRegistry
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.Player
import net.minestom.server.entity.pathfinding.PPath
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ModeledEntity(val owner: ModelOwner) {

    private companion object {
        const val TICK_DELTA = 1f / 20f
        const val WALK_DELTA_SQ_THRESHOLD = 2.5e-7
        const val WALK_TELEPORT_SQ_THRESHOLD = 4.0
        const val WALK_STILL_TICKS = 6
    }

    private var lastTickX: Double = Double.NaN
    private var lastTickZ: Double = Double.NaN
    private var stillTicks = 0
    private var walking = false
    private val ownerEntity: Entity? = (owner as? EntityModelOwner)?.entity
    private var hitbox: ModelHitbox? = null

    val entityOrNull: Entity? get() = (owner as? EntityModelOwner)?.entity

    private val _models = ConcurrentHashMap<String, ActiveModel>()
    val models: Map<String, ActiveModel> get() = _models

    private val _viewers: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    val viewers: Set<UUID> get() = _viewers

    var headYaw: Float = 0f
    var headPitch: Float = 0f

    fun addModel(id: String, blueprint: ModelBlueprint, autoPlayIdle: Boolean = true, autoPlayWalk: Boolean = true): ActiveModel {
        val model = ActiveModel(blueprint, autoPlayIdle, autoPlayWalk)
        _models[id] = model
        _viewers.forEach { uuid ->
            findPlayer(uuid)?.let { model.show(it, owner.position) }
        }
        model.initBehaviors(this)

        if (hitbox == null && ownerEntity != null && blueprint.hitboxWidth > 0f && blueprint.hitboxHeight > 0f) {
            val h = ModelHitbox(ownerEntity, blueprint.hitboxWidth, blueprint.hitboxHeight)
            hitbox = h
            h.spawn()
        }

        for (bone in model.bones.values) {
            val mountBehavior = bone.behavior<MountBehavior>() ?: continue
            SeatRegistry.register(mountBehavior.seatEntityId, SeatRegistry.Binding(
                mountBehavior = mountBehavior,
                modeledEntity = this,
                controllerFactory = { PassiveMountController },
            ))
        }

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
        updateWalkState()
        hitbox?.tick()
        _models.values.forEach { model ->
            model.tickAnimations(TICK_DELTA)
            model.computeTransforms()
            model.tickBehaviors(this)
            model.updateRenderer(owner.position)
        }
    }

    private fun updateWalkState() {
        val current = owner.position
        val moving = isOwnerMoving(current.x(), current.z())
        lastTickX = current.x()
        lastTickZ = current.z()

        if (moving) {
            stillTicks = 0
            if (!walking) {
                walking = true
                for (model in _models.values) {
                    if (!model.autoPlayWalk) continue
                    val walkName = model.walkAnimationName ?: continue
                    model.idleAnimationName?.let { model.stopAnimation(it) }
                    model.playAnimation(walkName, lerpIn = 0.15f, lerpOut = 0.15f)
                }
            }
        } else if (walking) {
            if (++stillTicks >= WALK_STILL_TICKS) {
                walking = false
                stillTicks = 0
                for (model in _models.values) {
                    if (!model.autoPlayWalk) continue
                    model.walkAnimationName?.let { model.stopAnimation(it) }
                    model.idleAnimationName?.let {
                        if (!model.isPlayingAnimation(it)) model.playAnimation(it, lerpIn = 0.15f, lerpOut = 0.15f)
                    }
                }
            }
        }
    }

    private fun isOwnerMoving(currentX: Double, currentZ: Double): Boolean {
        val creature = ownerEntity as? EntityCreature
        if (creature != null) {
            val state = creature.navigator.state
            if (state == PPath.State.FOLLOWING ||
                state == PPath.State.CALCULATING ||
                state == PPath.State.COMPUTED ||
                state == PPath.State.TERMINATING
            ) return true
        }
        if (lastTickX.isNaN()) return false
        val dx = currentX - lastTickX
        val dz = currentZ - lastTickZ
        val deltaSq = dx * dx + dz * dz
        return deltaSq > WALK_DELTA_SQ_THRESHOLD && deltaSq < WALK_TELEPORT_SQ_THRESHOLD
    }

    fun destroy() {
        SeatRegistry.unregisterAllOf(this)
        hitbox?.remove()
        hitbox = null
        _models.values.forEach {
            it.destroyBehaviors(this)
            it.destroy()
        }
        _models.clear()
        _viewers.clear()
        ModelEngine.unregisterModeledEntity(owner)
    }

    private fun findPlayer(uuid: UUID): Player? =
        MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid)
}

class ModeledEntityBuilder @PublishedApi internal constructor(
    private val owner: ModelOwner,
    private val modeledEntity: ModeledEntity,
) {
    fun model(id: String, autoPlayIdle: Boolean = true, autoPlayWalk: Boolean = true, block: ActiveModelBuilder.() -> Unit = {}) {
        val blueprint = ModelEngine.blueprint(id)
        val model = modeledEntity.addModel(id, blueprint, autoPlayIdle, autoPlayWalk)
        ActiveModelBuilder(model).apply(block)
    }
}

class ActiveModelBuilder @PublishedApi internal constructor(private val model: ActiveModel) {
    fun scale(scale: Float) { model.modelScale = scale }
    fun animation(name: String, lerpIn: Float = 0f, lerpOut: Float = 0f, speed: Float = 1f) {
        model.playAnimation(name, lerpIn, lerpOut, speed)
    }
}
