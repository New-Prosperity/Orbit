package me.nebula.orbit.utils.modelengine.lod

import me.nebula.orbit.utils.modelengine.model.ActiveModel
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class LODHandler(
    private val config: LODConfig,
) {
    private val playerLevels = ConcurrentHashMap<UUID, Int>()

    fun evaluate(modeledEntity: ModeledEntity) {
        val entityPos = modeledEntity.owner.position
        var closestDistSq = Double.MAX_VALUE

        modeledEntity.viewers.forEach { uuid ->
            val player = findPlayer(uuid) ?: return@forEach
            val distSq = player.position.distanceSquared(entityPos)

            if (distSq > config.cullDistance * config.cullDistance) {
                if (playerLevels.put(uuid, -1) != -1) {
                    modeledEntity.hide(player)
                }
                return@forEach
            }

            if (playerLevels.put(uuid, 0) == -1) {
                modeledEntity.show(player)
            }

            if (distSq < closestDistSq) closestDistSq = distSq
        }

        if (closestDistSq < Double.MAX_VALUE) {
            val dist = kotlin.math.sqrt(closestDistSq)
            val levelIndex = config.levels.indexOfFirst { dist <= it.maxDistance }
                .takeIf { it >= 0 } ?: (config.levels.size - 1)
            val level = config.levels.getOrNull(levelIndex) ?: return
            applyLevel(modeledEntity, level)
        }
    }

    fun tickRate(playerUuid: UUID): Int {
        val levelIndex = playerLevels[playerUuid] ?: return 1
        if (levelIndex < 0) return Int.MAX_VALUE
        return config.levels.getOrNull(levelIndex)?.tickRate ?: 1
    }

    fun cleanup(playerUuid: UUID) {
        playerLevels.remove(playerUuid)
    }

    private fun applyLevel(modeledEntity: ModeledEntity, level: LODLevel) {
        modeledEntity.models.values.forEach { model ->
            applyLevelToModel(model, level)
        }
    }

    private fun applyLevelToModel(model: ActiveModel, level: LODLevel) {
        if (level.visibleBones != null) {
            model.bones.values.forEach { bone ->
                bone.visible = bone.blueprint.name in level.visibleBones
            }
            return
        }
        if (level.hiddenBones != null) {
            model.bones.values.forEach { bone ->
                bone.visible = bone.blueprint.name !in level.hiddenBones
            }
            return
        }
        model.bones.values.forEach { bone ->
            bone.visible = bone.blueprint.visible
        }
    }

    private fun findPlayer(uuid: UUID): Player? =
        MinecraftServer.getConnectionManager().onlinePlayers.firstOrNull { it.uuid == uuid }
}
