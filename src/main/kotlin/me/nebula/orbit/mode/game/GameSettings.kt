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
) {
    init {
        require(countdownSeconds >= 0) { "countdownSeconds must be >= 0 (got $countdownSeconds)" }
        require(gameDurationSeconds >= 0) { "gameDurationSeconds must be >= 0 (got $gameDurationSeconds)" }
        require(endingDurationSeconds >= 0) { "endingDurationSeconds must be >= 0 (got $endingDurationSeconds)" }
        require(gracePeriodSeconds >= 0) { "gracePeriodSeconds must be >= 0 (got $gracePeriodSeconds)" }
        require(minPlayers >= 1) { "minPlayers must be >= 1 (got $minPlayers)" }
        require(maxPlayers >= minPlayers) { "maxPlayers ($maxPlayers) must be >= minPlayers ($minPlayers)" }
        require(disconnectEliminationSeconds >= 0) { "disconnectEliminationSeconds must be >= 0 (got $disconnectEliminationSeconds)" }
        require(reconnectWindowSeconds >= 0) { "reconnectWindowSeconds must be >= 0 (got $reconnectWindowSeconds)" }
        require(combatLogSeconds >= 0) { "combatLogSeconds must be >= 0 (got $combatLogSeconds)" }
        require(afkEliminationSeconds >= 0) { "afkEliminationSeconds must be >= 0 (got $afkEliminationSeconds)" }
        require(minViablePlayers >= 0) { "minViablePlayers must be >= 0 (got $minViablePlayers)" }
        require(minViablePlayers <= maxPlayers) { "minViablePlayers ($minViablePlayers) must be <= maxPlayers ($maxPlayers)" }
    }
}

data class TeamConfig(
    val teamCount: Int,
    val minTeamSize: Int = 1,
    val maxTeamSize: Int = Int.MAX_VALUE,
    val autoBalance: Boolean = true,
    val friendlyFire: Boolean = false,
    val teamNames: List<String> = emptyList(),
) {
    init {
        require(teamCount >= 1) { "teamCount must be >= 1 (got $teamCount)" }
        require(minTeamSize >= 1) { "minTeamSize must be >= 1 (got $minTeamSize)" }
        require(maxTeamSize >= minTeamSize) { "maxTeamSize ($maxTeamSize) must be >= minTeamSize ($minTeamSize)" }
        require(teamNames.isEmpty() || teamNames.size == teamCount) {
            "teamNames must be empty or have exactly teamCount=$teamCount entries (got ${teamNames.size})"
        }
    }
}

data class RespawnConfig(
    val respawnDelayTicks: Int = 60,
    val maxLives: Int = 0,
    val invincibilityTicks: Int = 40,
    val clearInventoryOnRespawn: Boolean = false,
) {
    init {
        require(respawnDelayTicks >= 0) { "respawnDelayTicks must be >= 0 (got $respawnDelayTicks)" }
        require(maxLives >= 0) { "maxLives must be >= 0 (got $maxLives)" }
        require(invincibilityTicks >= 0) { "invincibilityTicks must be >= 0 (got $invincibilityTicks)" }
    }
}

data class LateJoinConfig(
    val windowSeconds: Int = 30,
    val joinAsSpectator: Boolean = false,
    val maxLateJoiners: Int = 0,
) {
    init {
        require(windowSeconds >= 0) { "windowSeconds must be >= 0 (got $windowSeconds)" }
        require(maxLateJoiners >= 0) { "maxLateJoiners must be >= 0 (got $maxLateJoiners)" }
    }
}

data class OvertimeConfig(
    val durationSeconds: Int = 60,
    val suddenDeath: Boolean = false,
) {
    init {
        require(durationSeconds >= 0) { "durationSeconds must be >= 0 (got $durationSeconds)" }
    }
}

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
) {
    init {
        require(worldPath.isNotBlank()) { "worldPath must not be blank" }
        require(preloadRadius >= 0) { "preloadRadius must be >= 0 (got $preloadRadius)" }
    }
}
