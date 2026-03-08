package me.nebula.orbit.mode.game

import me.nebula.orbit.mode.config.CosmeticConfig
import me.nebula.orbit.mode.config.HotbarItemConfig
import me.nebula.orbit.mode.config.LobbyConfig
import me.nebula.orbit.mode.config.LobbyWorldConfig
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
    val freezeDuringCountdown: Boolean = false,
    val combatLogSeconds: Int = 0,
    val afkEliminationSeconds: Int = 0,
    val minViablePlayers: Int = 0,
    val isolateSpectatorChat: Boolean = false,
    val voidDeathY: Double = Double.NEGATIVE_INFINITY,
)

data class TeamConfig(
    val teamCount: Int,
    val minTeamSize: Int = 1,
    val maxTeamSize: Int = Int.MAX_VALUE,
    val autoBalance: Boolean = true,
    val friendlyFire: Boolean = false,
    val teamNames: List<String> = emptyList(),
)

data class RespawnConfig(
    val respawnDelayTicks: Int = 60,
    val maxLives: Int = 0,
    val invincibilityTicks: Int = 40,
    val clearInventoryOnRespawn: Boolean = false,
)

data class LateJoinConfig(
    val windowSeconds: Int = 30,
    val joinAsSpectator: Boolean = false,
    val maxLateJoiners: Int = 0,
)

data class OvertimeConfig(
    val durationSeconds: Int = 60,
    val suddenDeath: Boolean = false,
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
    val teams: TeamConfig? = null,
    val respawn: RespawnConfig? = null,
    val lateJoin: LateJoinConfig? = null,
    val overtime: OvertimeConfig? = null,
    val mapName: String? = null,
    val lobbyWorld: LobbyWorldConfig? = null,
)
