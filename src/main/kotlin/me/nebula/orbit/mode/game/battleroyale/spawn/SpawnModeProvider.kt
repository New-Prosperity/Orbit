package me.nebula.orbit.mode.game.battleroyale.spawn

import me.nebula.orbit.mode.game.battleroyale.SpawnModeConfig
import me.nebula.orbit.mode.game.battleroyale.SpawnModeResult
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance
import java.util.UUID
import kotlin.random.Random

interface SpawnModeProvider {
    val id: String

    fun execute(context: SpawnContext): SpawnModeResult
}

data class SpawnContext(
    val config: SpawnModeConfig,
    val players: List<Player>,
    val instance: Instance,
    val center: Pos,
    val mapRadius: Int,
    val onPlayerReady: (Player, Pos) -> Unit,
    val onComplete: (() -> Unit)? = null,
    val random: Random = Random.Default,
    val teamOf: (UUID) -> String? = { null },
    val onImmunityGrant: (UUID, Pos) -> Unit = { _, _ -> },
)
