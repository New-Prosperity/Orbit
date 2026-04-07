package me.nebula.orbit.utils.spectatortoolkit

import net.minestom.server.tag.Tag
import java.util.UUID

internal object SpectatorTags {
    val SPEED_INDEX: Tag<Int> = Tag.Integer("spectator:speed_index")
    val ACTIVE: Tag<Boolean> = Tag.Boolean("spectator:active")
    val CURRENT_TARGET: Tag<UUID> = Tag.UUID("spectator:current_target")
    val FREECAM: Tag<Boolean> = Tag.Boolean("spectator:freecam")
    val FREECAM_LAST_TARGET: Tag<UUID> = Tag.UUID("spectator:freecam_last_target")
}

data class SpectatorTargetStats(
    val kills: Int = 0,
    val team: String? = null,
    val kit: String? = null,
)
