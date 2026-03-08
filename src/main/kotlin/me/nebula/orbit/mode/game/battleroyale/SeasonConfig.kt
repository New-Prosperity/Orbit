package me.nebula.orbit.mode.game.battleroyale

import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.HotbarItemConfig
import me.nebula.orbit.mode.config.LobbyConfig
import me.nebula.orbit.mode.config.LobbyWorldConfig
import me.nebula.orbit.mode.config.ScoreboardConfig
import me.nebula.orbit.mode.config.SpawnConfig
import me.nebula.orbit.mode.config.TabListConfig
import me.nebula.orbit.mode.game.TimingConfig
import me.nebula.orbit.mode.game.battleroyale.seasons.season1
import me.nebula.orbit.utils.chestloot.ChestLootTable

data class Season(
    val id: Int,
    val kits: List<KitDefinitionConfig>,
    val xpRewards: Map<String, Long>,
    val starterKit: StarterKitConfig,
    val voteCategories: List<VoteCategoryDef> = emptyList(),
    val lootTables: List<ChestLootTable> = emptyList(),
    val worldPath: String = "worlds/battleroyale",
    val preloadRadius: Int = 8,
    val spawn: SpawnConfig = SpawnConfig(0.5, 65.0, 0.5, 0f, 0f),
    val scoreboard: ScoreboardConfig = ScoreboardConfig("orbit.game.br.scoreboard.title", 2, emptyList()),
    val tabList: TabListConfig = TabListConfig(3, "orbit.game.br.tablist.header", "orbit.game.br.tablist.footer"),
    val lobby: LobbyConfig = LobbyConfig("ADVENTURE", protectBlocks = true, disableDamage = true, disableHunger = true, lockInventory = false, voidTeleportY = -64.0),
    val hotbar: List<HotbarItemConfig> = emptyList(),
    val timing: TimingConfig = TimingConfig(countdownSeconds = 15, endingDurationSeconds = 10, minPlayers = 2, maxPlayers = 16),
    val border: BorderConfig = BorderConfig(500.0, 20.0, 0.0, 0.0, 120, 300),
    val borderPhases: List<BorderPhaseConfig> = emptyList(),
    val borderDamagePerSecond: Float = 1f,
    val spawnMode: SpawnModeConfig = SpawnModeConfig(),
    val goldenHead: GoldenHeadConfig = GoldenHeadConfig(),
    val deathmatch: DeathmatchConfig = DeathmatchConfig(),
    val cosmetics: CosmeticConfig = CosmeticConfig(),
    val mapPreset: String? = null,
    val lobbyWorld: LobbyWorldConfig? = null,
)

data class VoteCategoryDef(
    val id: String,
    val nameKey: String,
    val material: String,
    val defaultIndex: Int = 1,
    val options: List<VoteOptionDef>,
)

data class VoteOptionDef(
    val nameKey: String,
    val material: String,
    val value: Int,
)

object SeasonConfig {
    val current: Season = season1()
}
