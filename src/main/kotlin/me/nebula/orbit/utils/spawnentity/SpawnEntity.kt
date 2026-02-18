package me.nebula.orbit.utils.spawnentity

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.instance.Instance

private val miniMessage = MiniMessage.miniMessage()

class SpawnEntityBuilder @PublishedApi internal constructor(private val entityType: EntityType) {

    @PublishedApi internal var position: Pos = Pos.ZERO
    @PublishedApi internal var instance: Instance? = null
    @PublishedApi internal var velocity: Vec? = null
    @PublishedApi internal var customName: Component? = null
    @PublishedApi internal var customNameVisible: Boolean = false
    @PublishedApi internal var noGravity: Boolean = false
    @PublishedApi internal var silent: Boolean = false
    @PublishedApi internal var invisible: Boolean = false
    @PublishedApi internal var glowing: Boolean = false
    @PublishedApi internal var health: Float? = null
    @PublishedApi internal var onSpawnHandler: ((Entity) -> Unit)? = null

    fun position(pos: Pos) { position = pos }
    fun position(x: Double, y: Double, z: Double) { position = Pos(x, y, z) }
    fun instance(inst: Instance) { instance = inst }
    fun velocity(vec: Vec) { velocity = vec }
    fun velocity(x: Double, y: Double, z: Double) { velocity = Vec(x, y, z) }
    fun customName(name: String) {
        customName = miniMessage.deserialize(name)
        customNameVisible = true
    }
    fun customName(component: Component) {
        customName = component
        customNameVisible = true
    }
    fun customNameVisible(visible: Boolean) { customNameVisible = visible }
    fun noGravity(value: Boolean = true) { noGravity = value }
    fun silent(value: Boolean = true) { silent = value }
    fun invisible(value: Boolean = true) { invisible = value }
    fun glowing(value: Boolean = true) { glowing = value }
    fun health(hp: Float) { health = hp }
    fun onSpawn(handler: (Entity) -> Unit) { onSpawnHandler = handler }

    @PublishedApi internal fun build(): Entity {
        val inst = requireNotNull(instance) { "Instance must be set for entity spawning" }
        val entity = tryCreateLiving(entityType) ?: Entity(entityType)

        customName?.let { entity.customName = it }
        entity.isCustomNameVisible = customNameVisible
        entity.setNoGravity(noGravity)
        entity.isSilent = silent
        entity.isInvisible = invisible
        entity.isGlowing = glowing

        if (entity is LivingEntity) {
            health?.let { entity.health = it }
        }

        entity.setInstance(inst, position)

        velocity?.let { entity.velocity = it }

        onSpawnHandler?.invoke(entity)

        return entity
    }

    private fun tryCreateLiving(type: EntityType): LivingEntity? =
        try {
            EntityCreature(type)
        } catch (_: Exception) {
            null
        }
}

inline fun spawnEntity(entityType: EntityType, block: SpawnEntityBuilder.() -> Unit): Entity =
    SpawnEntityBuilder(entityType).apply(block).build()

inline fun Instance.spawnEntity(entityType: EntityType, block: SpawnEntityBuilder.() -> Unit): Entity =
    SpawnEntityBuilder(entityType).apply {
        instance(this@spawnEntity)
        block()
    }.build()
