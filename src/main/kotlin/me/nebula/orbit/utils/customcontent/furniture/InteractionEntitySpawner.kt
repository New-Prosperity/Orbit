package me.nebula.orbit.utils.customcontent.furniture

import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.InteractionMeta
import net.minestom.server.instance.Instance

object InteractionEntitySpawner {

    fun spawnAt(instance: Instance, x: Int, y: Int, z: Int): Entity {
        val entity = Entity(EntityType.INTERACTION)
        val meta = entity.entityMeta as InteractionMeta
        meta.setNotifyAboutChanges(false)
        meta.setWidth(1f)
        meta.setHeight(1f)
        meta.setResponse(true)
        meta.setNotifyAboutChanges(true)
        entity.setInstance(instance, Pos(x + 0.5, y.toDouble(), z + 0.5))
        return entity
    }

    fun despawn(instance: Instance, entityId: Int) {
        instance.entities.firstOrNull { it.entityId == entityId }?.remove()
    }
}
