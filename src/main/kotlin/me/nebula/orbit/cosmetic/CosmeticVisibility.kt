package me.nebula.orbit.cosmetic

import me.nebula.gravity.player.PreferenceStore
import me.nebula.orbit.utils.modelengine.model.ModeledEntity
import net.minestom.server.coordinate.Point
import net.minestom.server.entity.Player
import java.util.UUID

enum class CosmeticDisplayMode { FULL, REDUCED, NONE }

object CosmeticVisibility {

    fun displayModeOf(playerId: UUID): CosmeticDisplayMode =
        when (PreferenceStore.load(playerId)?.cosmeticDisplay) {
            "REDUCED" -> CosmeticDisplayMode.REDUCED
            "NONE" -> CosmeticDisplayMode.NONE
            else -> CosmeticDisplayMode.FULL
        }

    fun shouldShowModel(viewer: Player, ownerUuid: UUID): Boolean {
        if (viewer.uuid == ownerUuid) return true
        return displayModeOf(viewer.uuid) == CosmeticDisplayMode.FULL
    }

    fun shouldShowParticles(viewer: Player, ownerUuid: UUID): Boolean {
        if (viewer.uuid == ownerUuid) return true
        return displayModeOf(viewer.uuid) != CosmeticDisplayMode.NONE
    }

    fun updateViewers(
        modeled: ModeledEntity,
        players: Collection<Player>,
        ownerUuid: UUID,
        referencePos: Point,
        maxDistance: Double = 48.0,
        ensureOwnerVisible: Boolean = true,
    ) {
        for (player in players) {
            if (ensureOwnerVisible && player.uuid == ownerUuid) {
                if (player.uuid !in modeled.viewers) modeled.show(player)
                continue
            }
            val inRange = player.position.distance(referencePos) < maxDistance
            val shouldShow = inRange && shouldShowModel(player, ownerUuid)
            if (shouldShow && player.uuid !in modeled.viewers) {
                modeled.show(player)
            } else if (!shouldShow && player.uuid in modeled.viewers) {
                modeled.hide(player)
            }
        }
    }
}
