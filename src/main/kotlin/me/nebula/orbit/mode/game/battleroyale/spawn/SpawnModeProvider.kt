package me.nebula.orbit.mode.game.battleroyale.spawn

import me.nebula.orbit.mode.game.battleroyale.SpawnMode
import me.nebula.orbit.mode.game.battleroyale.SpawnModeConfig
import me.nebula.orbit.mode.game.battleroyale.SpawnModeExecutor
import me.nebula.orbit.mode.game.battleroyale.SpawnModeResult
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.instance.Instance

interface SpawnModeProvider {
    val id: String

    fun execute(
        config: SpawnModeConfig,
        players: List<Player>,
        instance: Instance,
        center: Pos,
        mapRadius: Int,
        onPlayerReady: (Player, Pos) -> Unit,
        onComplete: (() -> Unit)?,
    ): SpawnModeResult
}

internal class EnumBackedSpawnProvider(
    override val id: String,
    private val mode: SpawnMode,
) : SpawnModeProvider {
    override fun execute(
        config: SpawnModeConfig,
        players: List<Player>,
        instance: Instance,
        center: Pos,
        mapRadius: Int,
        onPlayerReady: (Player, Pos) -> Unit,
        onComplete: (() -> Unit)?,
    ): SpawnModeResult = SpawnModeExecutor.execute(
        config.copy(mode = mode), players, instance, center, mapRadius, onPlayerReady, onComplete,
    )
}
