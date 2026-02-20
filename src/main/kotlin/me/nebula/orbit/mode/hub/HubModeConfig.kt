package me.nebula.orbit.mode.hub

import me.nebula.orbit.mode.config.HotbarItemConfig
import me.nebula.orbit.mode.config.LobbyConfig
import me.nebula.orbit.mode.config.ScoreboardConfig
import me.nebula.orbit.mode.config.SpawnConfig
import me.nebula.orbit.mode.config.TabListConfig

data class HubModeConfig(
    val worldPath: String,
    val preloadRadius: Int,
    val spawn: SpawnConfig,
    val scoreboard: ScoreboardConfig,
    val tabList: TabListConfig,
    val lobby: LobbyConfig,
    val hotbar: List<HotbarItemConfig>,
    val selector: SelectorConfig,
)

data class SelectorConfig(
    val title: String,
    val rows: Int,
    val border: String,
)
