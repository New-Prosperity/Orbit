package me.nebula.orbit.utils.modelengine.hitbox

import net.minestom.server.collision.BoundingBox
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.InteractionMeta

class ModelHitbox(
    private val owner: Entity,
    width: Float,
    height: Float,
) {
    private val interaction: Entity = Entity(EntityType.INTERACTION)

    init {
        val meta = interaction.entityMeta as InteractionMeta
        meta.width = width
        meta.height = height
        meta.response = true
        interaction.boundingBox = BoundingBox(width.toDouble(), height.toDouble(), width.toDouble())
        interaction.isInvisible = false
        interaction.isAutoViewable = true
    }

    val entity: Entity get() = interaction

    fun spawn() {
        val instance = owner.instance ?: return
        interaction.setInstance(instance, owner.position)
    }

    fun tick() {
        if (interaction.isRemoved) return
        val pos = owner.position
        if (interaction.position == pos) return
        interaction.teleport(pos)
    }

    fun remove() {
        if (!interaction.isRemoved) interaction.remove()
    }
}
