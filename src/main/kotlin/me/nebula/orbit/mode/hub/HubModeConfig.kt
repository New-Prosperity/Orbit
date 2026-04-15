package me.nebula.orbit.mode.hub

import me.nebula.ether.utils.translation.asTranslationKey
import me.nebula.gravity.cosmetic.CosmeticCategory
import me.nebula.orbit.mode.config.CosmeticConfig
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
    val cosmetics: CosmeticConfig? = null,
)

data class SelectorConfig(
    val title: String,
    val rows: Int,
    val border: String,
    val items: List<SelectorItemConfig> = emptyList(),
)

data class SelectorItemConfig(
    val gameMode: String,
    val slot: Int,
    val material: String,
)

object HubDefinitions {

    val CONFIG = HubModeConfig(
        worldPath = "worlds/hub",
        preloadRadius = 6,
        spawn = SpawnConfig(0.5, 65.0, 0.5, 0f, 0f),
        scoreboard = ScoreboardConfig(
            title = "orbit.hub.scoreboard.title",
            refreshSeconds = 5,
            lines = listOf(
                "",
                "orbit.hub.scoreboard.online",
                "orbit.hub.scoreboard.rank",
                "",
                "orbit.hub.scoreboard.server",
                "",
                "orbit.hub.scoreboard.ip",
            ),
        ),
        tabList = TabListConfig(
            refreshSeconds = 5,
            header = "orbit.hub.tablist.header".asTranslationKey(),
            footer = "orbit.hub.tablist.footer".asTranslationKey(),
        ),
        lobby = LobbyConfig(
            gameMode = "ADVENTURE",
            protectBlocks = true,
            disableDamage = true,
            disableHunger = true,
            lockInventory = true,
            voidTeleportY = -64.0,
        ),
        hotbar = listOf(
            HotbarItemConfig(0, "minecraft:emerald", "<green><bold>Cosmetics", glowing = false, action = "open_cosmetics"),
            HotbarItemConfig(2, "minecraft:experience_bottle", "<gold><bold>Battle Pass", glowing = false, action = "open_battlepass"),
            HotbarItemConfig(4, "minecraft:compass", "<green><bold>Server Selector", glowing = true, action = "open_selector"),
            HotbarItemConfig(6, "minecraft:paper", "<yellow><bold>Missions", glowing = false, action = "open_missions"),
            HotbarItemConfig(8, "minecraft:diamond", "<aqua><bold>Achievements", glowing = false, action = "open_achievements"),
        ),
        selector = SelectorConfig(
            title = "orbit.selector.title",
            rows = 3,
            border = "minecraft:gray_stained_glass_pane",
            items = listOf(
                SelectorItemConfig("battleroyale", 13, "minecraft:iron_sword"),
            ),
        ),
        cosmetics = CosmeticConfig(
            enabledCategories = setOf(
                CosmeticCategory.ARMOR_SKIN.name,
                CosmeticCategory.KILL_EFFECT.name,
                CosmeticCategory.TRAIL.name,
                CosmeticCategory.WIN_EFFECT.name,
                CosmeticCategory.PROJECTILE_TRAIL.name,
            ),
        ),
    )
}
