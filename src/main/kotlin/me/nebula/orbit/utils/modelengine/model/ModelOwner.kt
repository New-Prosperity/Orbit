package me.nebula.orbit.utils.modelengine.model

import me.nebula.orbit.utils.modelengine.ModelEngine
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.Player
import java.util.concurrent.atomic.AtomicInteger

interface ModelOwner {
    val position: Pos
    val isRemoved: Boolean
    val ownerId: Int
}

class EntityModelOwner(val entity: Entity) : ModelOwner {
    override val position: Pos get() = entity.position
    override val isRemoved: Boolean get() = entity.isRemoved
    override val ownerId: Int get() = entity.entityId
}

fun Entity.asModelOwner(): ModelOwner = EntityModelOwner(this)

private val nextStandaloneId = AtomicInteger(-4_000_000)

class StandaloneModelOwner(
    position: Pos,
) : ModelOwner {

    override var position: Pos = position

    override val isRemoved: Boolean get() = _removed
    override val ownerId: Int = nextStandaloneId.getAndDecrement()

    @Volatile private var _removed = false

    @PublishedApi internal var modeledEntity: ModeledEntity? = null

    fun show(player: Player) {
        modeledEntity?.show(player)
    }

    fun hide(player: Player) {
        modeledEntity?.hide(player)
    }

    fun remove() {
        _removed = true
        modeledEntity?.destroy()
        modeledEntity = null
    }
}

inline fun standAloneModel(position: Pos, block: ModeledEntityBuilder.() -> Unit): StandaloneModelOwner {
    val owner = StandaloneModelOwner(position)
    val modeled = ModelEngine.createModeledEntity(owner)
    owner.modeledEntity = modeled
    ModeledEntityBuilder(owner, modeled).apply(block)
    return owner
}
