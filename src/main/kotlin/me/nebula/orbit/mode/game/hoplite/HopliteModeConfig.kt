package me.nebula.orbit.mode.game.hoplite

import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.HotbarItemConfig
import me.nebula.orbit.mode.config.LobbyConfig
import me.nebula.orbit.mode.config.ScoreboardConfig
import me.nebula.orbit.mode.config.SpawnConfig
import me.nebula.orbit.mode.config.TabListConfig
import me.nebula.orbit.mode.game.TimingConfig

data class HopliteModeConfig(
    val worldPath: String,
    val preloadRadius: Int,
    val spawn: SpawnConfig,
    val spawnPoints: List<SpawnConfig>,
    val scoreboard: ScoreboardConfig,
    val tabList: TabListConfig,
    val lobby: LobbyConfig,
    val hotbar: List<HotbarItemConfig>,
    val timing: TimingConfig,
    val border: BorderConfig,
    val kit: KitConfig,
    val cosmetics: CosmeticConfig? = null,
)

data class BorderConfig(
    val initialDiameter: Double,
    val finalDiameter: Double,
    val centerX: Double,
    val centerZ: Double,
    val shrinkStartSeconds: Int,
    val shrinkDurationSeconds: Int,
)

data class KitConfig(
    val helmet: String? = null,
    val chestplate: String? = null,
    val leggings: String? = null,
    val boots: String? = null,
    val items: List<KitItemConfig> = emptyList(),
)

data class KitItemConfig(
    val slot: Int,
    val material: String,
    val amount: Int = 1,
)
