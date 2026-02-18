package me.nebula.orbit.utils.entityspawnerpool

import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityCreature
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.attribute.Attribute
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

class EntityPool @PublishedApi internal constructor(
    val entityType: EntityType,
    private val initialSize: Int,
    private val onAcquireHandler: ((Entity) -> Unit)?,
    private val onReleaseHandler: ((Entity) -> Unit)?,
) {

    private val available = ConcurrentLinkedQueue<Entity>()
    private val inUse = ConcurrentLinkedQueue<Entity>()
    private val totalCreated = AtomicInteger(0)

    fun warmup() {
        repeat(initialSize) {
            val entity = createEntity()
            available.offer(entity)
            totalCreated.incrementAndGet()
        }
    }

    fun acquire(): Entity {
        val entity = available.poll() ?: createEntity().also { totalCreated.incrementAndGet() }
        inUse.offer(entity)
        onAcquireHandler?.invoke(entity)
        return entity
    }

    fun release(entity: Entity) {
        if (!inUse.remove(entity)) return
        onReleaseHandler?.invoke(entity)
        if (entity.instance != null) {
            entity.remove()
        }
        if (entity is LivingEntity) {
            entity.health = entity.getAttributeValue(Attribute.MAX_HEALTH).toFloat()
        }
        entity.velocity = Vec.ZERO
        available.offer(entity)
    }

    fun releaseAll() {
        inUse.toList().forEach { release(it) }
    }

    fun destroy() {
        releaseAll()
        available.forEach { it.remove() }
        available.clear()
        totalCreated.set(0)
    }

    val availableCount: Int get() = available.size
    val inUseCount: Int get() = inUse.size
    val totalCount: Int get() = totalCreated.get()

    private fun createEntity(): Entity =
        try {
            EntityCreature(entityType)
        } catch (_: Exception) {
            Entity(entityType)
        }
}

class EntityPoolBuilder @PublishedApi internal constructor(
    private val entityType: EntityType,
    private val poolSize: Int,
) {

    @PublishedApi internal var onAcquireHandler: ((Entity) -> Unit)? = null
    @PublishedApi internal var onReleaseHandler: ((Entity) -> Unit)? = null

    fun onAcquire(handler: (Entity) -> Unit) { onAcquireHandler = handler }
    fun onRelease(handler: (Entity) -> Unit) { onReleaseHandler = handler }

    @PublishedApi internal fun build(): EntityPool = EntityPool(
        entityType = entityType,
        initialSize = poolSize,
        onAcquireHandler = onAcquireHandler,
        onReleaseHandler = onReleaseHandler,
    )
}

inline fun entityPool(
    entityType: EntityType,
    poolSize: Int = 20,
    block: EntityPoolBuilder.() -> Unit = {},
): EntityPool = EntityPoolBuilder(entityType, poolSize).apply(block).build().also { it.warmup() }
