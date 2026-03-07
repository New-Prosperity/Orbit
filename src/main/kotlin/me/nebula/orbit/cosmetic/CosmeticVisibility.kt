package me.nebula.orbit.cosmetic

import me.nebula.gravity.player.PreferenceStore
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
}
