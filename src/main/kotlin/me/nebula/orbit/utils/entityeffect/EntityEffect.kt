package me.nebula.orbit.utils.entityeffect

import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.instance.Instance
import net.minestom.server.timer.TaskSchedule

class EntityEffectConfig @PublishedApi internal constructor() {
    var entityType: EntityType = EntityType.ARMOR_STAND
    var durationTicks: Int = 40
    var position: Pos = Pos.ZERO
    lateinit var instance: Instance
    var onSpawn: (Entity) -> Unit = {}
    var onTick: (Entity, Int) -> Unit = { _, _ -> }
    var onRemove: (Entity) -> Unit = {}
}

inline fun entityEffect(block: EntityEffectConfig.() -> Unit) {
    val config = EntityEffectConfig().apply(block)
    val entity = Entity(config.entityType)
    entity.setInstance(config.instance, config.position)
    config.onSpawn(entity)

    var ticks = 0
    entity.scheduler().buildTask {
        ticks++
        config.onTick(entity, ticks)
        if (ticks >= config.durationTicks) {
            config.onRemove(entity)
            entity.remove()
        }
    }.repeat(TaskSchedule.tick(1)).schedule()

    entity.scheduler().buildTask {
        if (!entity.isRemoved) {
            config.onRemove(entity)
            entity.remove()
        }
    }.delay(TaskSchedule.tick(config.durationTicks + 1)).schedule()
}

fun spawnTemporaryEntity(
    instance: Instance,
    type: EntityType,
    pos: Pos,
    durationTicks: Int = 40,
): Entity {
    val entity = Entity(type)
    entity.setInstance(instance, pos)
    entity.scheduler().buildTask {
        entity.remove()
    }.delay(TaskSchedule.tick(durationTicks)).schedule()
    return entity
}
