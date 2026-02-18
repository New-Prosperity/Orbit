package me.nebula.orbit.utils.entitystack

import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.coordinate.Vec
import java.util.concurrent.ConcurrentHashMap

data class StackedEntity(
    val base: Entity,
    val riders: MutableList<Entity> = mutableListOf(),
) {
    val size: Int get() = 1 + riders.size

    fun addRider(entity: Entity) {
        val top = if (riders.isEmpty()) base else riders.last()
        top.addPassenger(entity)
        riders.add(entity)
    }

    fun removeTop(): Entity? {
        if (riders.isEmpty()) return null
        val top = riders.removeLast()
        val parent = if (riders.isEmpty()) base else riders.last()
        parent.removePassenger(top)
        return top
    }

    fun removeAll() {
        riders.reversed().forEach { rider ->
            val index = riders.indexOf(rider)
            val parent = if (index == 0) base else riders[index - 1]
            parent.removePassenger(rider)
            rider.remove()
        }
        riders.clear()
    }

    fun despawn() {
        removeAll()
        base.remove()
    }
}

object EntityStackManager {

    private val stacks = ConcurrentHashMap<Int, StackedEntity>()

    fun createStack(instance: Instance, position: net.minestom.server.coordinate.Point, baseType: EntityType): StackedEntity {
        val base = Entity(baseType)
        base.setInstance(instance, Vec(position.x(), position.y(), position.z()))
        val stack = StackedEntity(base)
        stacks[base.entityId] = stack
        return stack
    }

    fun getStack(baseEntity: Entity): StackedEntity? = stacks[baseEntity.entityId]

    fun removeStack(baseEntity: Entity) {
        stacks.remove(baseEntity.entityId)?.despawn()
    }

    fun all(): Collection<StackedEntity> = stacks.values

    fun clear() {
        stacks.values.forEach { it.despawn() }
        stacks.clear()
    }
}
