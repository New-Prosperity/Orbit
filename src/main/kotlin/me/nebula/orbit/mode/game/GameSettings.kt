package me.nebula.orbit.mode.game

import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.HotbarItemConfig
import me.nebula.orbit.mode.config.LobbyConfig
import me.nebula.orbit.mode.config.ScoreboardConfig
import me.nebula.orbit.mode.config.SpawnConfig
import me.nebula.orbit.mode.config.TabListConfig

data class TimingConfig(
    val countdownSeconds: Int,
    val gameDurationSeconds: Int = 0,
    val endingDurationSeconds: Int,
    val gracePeriodSeconds: Int = 0,
    val minPlayers: Int,
    val maxPlayers: Int,
    val allowReconnect: Boolean = true,
    val disconnectEliminationSeconds: Int = 0,
    val reconnectWindowSeconds: Int = 0,
)

data class GameSettings(
    val worldPath: String,
    val preloadRadius: Int,
    val spawn: SpawnConfig,
    val scoreboard: ScoreboardConfig,
    val tabList: TabListConfig,
    val lobby: LobbyConfig,
    val hotbar: List<HotbarItemConfig>,
    val timing: TimingConfig,
    val cosmetics: CosmeticConfig? = null,
)
