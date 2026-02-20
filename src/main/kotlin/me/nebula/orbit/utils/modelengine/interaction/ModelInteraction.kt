package me.nebula.orbit.utils.modelengine.interaction

import me.nebula.orbit.utils.modelengine.ModelEngine
import me.nebula.orbit.utils.modelengine.behavior.SubHitboxBehavior
import me.nebula.orbit.utils.modelengine.bone.ModelBone
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import me.nebula.orbit.utils.raytrace.lookDirection
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Player

data class ModelHitResult(
    val modeledEntity: ModeledEntity,
    val hitbox: SubHitboxBehavior,
    val distance: Double,
)

object ModelInteraction {

    fun raycast(player: Player, maxDistance: Double = 5.0): ModelHitResult? {
        val eyePos = Vec(player.position.x(), player.position.y() + player.eyeHeight, player.position.z())
        val direction = player.lookDirection()

        var closest: ModelHitResult? = null
        var closestDist = maxDistance

        for (modeled in ModelEngine.allModeledEntities()) {
            if (modeled.owner.isRemoved) continue
            val entityPos = modeled.owner.position
            val dx = entityPos.x() - eyePos.x()
            val dy = entityPos.y() - eyePos.y()
            val dz = entityPos.z() - eyePos.z()
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > maxDistance * maxDistance * 4) continue

            for (model in modeled.models.values) {
                for (bone in model.bones.values) {
                    for (hitbox in bone.behaviorsOf<SubHitboxBehavior>()) {
                        val dist = hitbox.obb.rayTrace(eyePos, direction, maxDistance) ?: continue
                        if (dist < closestDist) {
                            closestDist = dist
                            closest = ModelHitResult(modeled, hitbox, dist)
                        }
                    }
                }
            }
        }

        return closest
    }

    fun raycastAll(player: Player, maxDistance: Double = 5.0): List<ModelHitResult> {
        val eyePos = Vec(player.position.x(), player.position.y() + player.eyeHeight, player.position.z())
        val direction = player.lookDirection()
        val results = mutableListOf<ModelHitResult>()

        for (modeled in ModelEngine.allModeledEntities()) {
            if (modeled.owner.isRemoved) continue
            for (model in modeled.models.values) {
                for (bone in model.bones.values) {
                    for (hitbox in bone.behaviorsOf<SubHitboxBehavior>()) {
                        val dist = hitbox.obb.rayTrace(eyePos, direction, maxDistance) ?: continue
                        results += ModelHitResult(modeled, hitbox, dist)
                    }
                }
            }
        }

        return results.sortedBy { it.distance }
    }
}
