package me.nebula.orbit.mode.game

import me.nebula.orbit.mode.config.PlaceholderResolver
import me.nebula.orbit.utils.ceremony.Ceremony
import me.nebula.orbit.utils.deathrecap.DeathRecapTracker
import me.nebula.orbit.utils.gamechat.GameChatPipeline
import me.nebula.orbit.utils.hotbar.Hotbar
import me.nebula.orbit.utils.killfeed.KillFeed
import me.nebula.orbit.utils.kit.Kit
import me.nebula.orbit.utils.matchresult.MatchResult
import me.nebula.orbit.utils.matchresult.matchResult
import me.nebula.orbit.utils.rewards.RewardDistributor
import me.nebula.orbit.utils.spectatortoolkit.SpectatorToolkit
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityDamageEvent
import java.util.UUID
import kotlin.time.Duration

@DslMarker
annotation class GameModeDsl

@GameModeDsl
class GameModeBuilder @PublishedApi internal constructor(val name: String) {

    lateinit var settings: GameSettings

    @PublishedApi internal var placeholderResolver: (() -> PlaceholderResolver)? = null
    @PublishedApi internal var gameSetup: ((List<Player>) -> Unit)? = null
    @PublishedApi internal var winCondition: (() -> MatchResult?)? = null

    @PublishedApi internal var onWaitingStart: (() -> Unit)? = null
    @PublishedApi internal var onPlayerJoinWaiting: ((Player) -> Unit)? = null
    @PublishedApi internal var onPlayerLeaveWaiting: ((Player) -> Unit)? = null
    @PublishedApi internal var onCountdownTick: ((Duration) -> Unit)? = null
    @PublishedApi internal var onPlayingStart: (() -> Unit)? = null
    @PublishedApi internal var onPlayerEliminated: ((Player) -> Unit)? = null
    @PublishedApi internal var onPlayerDisconnected: ((Player) -> Unit)? = null
    @PublishedApi internal var onPlayerReconnected: ((Player) -> Unit)? = null
    @PublishedApi internal var onEndingStart: ((MatchResult) -> Unit)? = null
    @PublishedApi internal var onEndingComplete: (() -> Unit)? = null
    @PublishedApi internal var onGameReset: (() -> Unit)? = null
    @PublishedApi internal var onPlayerDeath: ((Player, Player?) -> Unit)? = null
    @PublishedApi internal var onPlayerRespawn: ((Player) -> Unit)? = null
    @PublishedApi internal var onTeamsAssigned: ((Map<String, List<UUID>>) -> Unit)? = null
    @PublishedApi internal var onLateJoin: ((Player) -> Unit)? = null
    @PublishedApi internal var onKillStreak: ((Player, Int) -> Unit)? = null
    @PublishedApi internal var onOvertimeStart: (() -> Unit)? = null
    @PublishedApi internal var onOvertimeEnd: (() -> Unit)? = null
    @PublishedApi internal var onAfkEliminated: ((Player) -> Unit)? = null
    @PublishedApi internal var onCombatLog: ((Player) -> Unit)? = null
    @PublishedApi internal var onPlayerDamaged: ((Player, Player?, Float, EntityDamageEvent) -> Boolean)? = null

    @PublishedApi internal var buildLobbyHotbar: (() -> Hotbar?)? = null
    @PublishedApi internal var buildRespawnKit: (() -> Kit?)? = null
    @PublishedApi internal var buildRespawnPosition: ((Player) -> Pos)? = null
    @PublishedApi internal var buildChatPipeline: (() -> GameChatPipeline?)? = null
    @PublishedApi internal var buildSpectatorToolkit: (() -> SpectatorToolkit?)? = null
    @PublishedApi internal var buildKillFeed: (() -> KillFeed?)? = null
    @PublishedApi internal var buildDeathRecapTracker: (() -> DeathRecapTracker?)? = null
    @PublishedApi internal var buildRewardDistributor: (() -> RewardDistributor?)? = null
    @PublishedApi internal var buildCeremony: ((MatchResult) -> Ceremony?)? = null

    fun placeholderResolver(action: () -> PlaceholderResolver) { placeholderResolver = action }
    fun gameSetup(action: (List<Player>) -> Unit) { gameSetup = action }
    fun winCondition(action: () -> MatchResult?) { winCondition = action }

    fun onWaitingStart(action: () -> Unit) { onWaitingStart = action }
    fun onPlayerJoinWaiting(action: (Player) -> Unit) { onPlayerJoinWaiting = action }
    fun onPlayerLeaveWaiting(action: (Player) -> Unit) { onPlayerLeaveWaiting = action }
    fun onCountdownTick(action: (Duration) -> Unit) { onCountdownTick = action }
    fun onPlayingStart(action: () -> Unit) { onPlayingStart = action }
    fun onPlayerEliminated(action: (Player) -> Unit) { onPlayerEliminated = action }
    fun onPlayerDisconnected(action: (Player) -> Unit) { onPlayerDisconnected = action }
    fun onPlayerReconnected(action: (Player) -> Unit) { onPlayerReconnected = action }
    fun onEndingStart(action: (MatchResult) -> Unit) { onEndingStart = action }
    fun onEndingComplete(action: () -> Unit) { onEndingComplete = action }
    fun onGameReset(action: () -> Unit) { onGameReset = action }
    fun onPlayerDeath(action: (Player, Player?) -> Unit) { onPlayerDeath = action }
    fun onPlayerRespawn(action: (Player) -> Unit) { onPlayerRespawn = action }
    fun onTeamsAssigned(action: (Map<String, List<UUID>>) -> Unit) { onTeamsAssigned = action }
    fun onLateJoin(action: (Player) -> Unit) { onLateJoin = action }
    fun onKillStreak(action: (Player, Int) -> Unit) { onKillStreak = action }
    fun onOvertimeStart(action: () -> Unit) { onOvertimeStart = action }
    fun onOvertimeEnd(action: () -> Unit) { onOvertimeEnd = action }
    fun onAfkEliminated(action: (Player) -> Unit) { onAfkEliminated = action }
    fun onCombatLog(action: (Player) -> Unit) { onCombatLog = action }
    fun onPlayerDamaged(action: (Player, Player?, Float, EntityDamageEvent) -> Boolean) { onPlayerDamaged = action }

    fun lobbyHotbar(action: () -> Hotbar?) { buildLobbyHotbar = action }
    fun respawnKit(action: () -> Kit?) { buildRespawnKit = action }
    fun respawnPosition(action: (Player) -> Pos) { buildRespawnPosition = action }
    fun chatPipeline(action: () -> GameChatPipeline?) { buildChatPipeline = action }
    fun spectatorToolkit(action: () -> SpectatorToolkit?) { buildSpectatorToolkit = action }
    fun killFeed(action: () -> KillFeed?) { buildKillFeed = action }
    fun deathRecap(action: () -> DeathRecapTracker?) { buildDeathRecapTracker = action }
    fun rewards(action: () -> RewardDistributor?) { buildRewardDistributor = action }
    fun ceremony(action: (MatchResult) -> Ceremony?) { buildCeremony = action }

    @PublishedApi internal fun build(): DslGameMode {
        check(::settings.isInitialized) { "GameMode '$name' must declare 'settings'" }
        val resolver = checkNotNull(placeholderResolver) { "GameMode '$name' must declare 'placeholderResolver { }'" }
        val setup = checkNotNull(gameSetup) { "GameMode '$name' must declare 'gameSetup { }'" }
        val win = checkNotNull(winCondition) { "GameMode '$name' must declare 'winCondition { }'" }
        return DslGameMode(this, resolver, setup, win)
    }
}

class DslGameMode @PublishedApi internal constructor(
    private val builder: GameModeBuilder,
    private val placeholderResolverFn: () -> PlaceholderResolver,
    private val gameSetupFn: (List<Player>) -> Unit,
    private val winConditionFn: () -> MatchResult?,
) : GameMode() {

    val name: String = builder.name

    override val settings: GameSettings = builder.settings

    override fun buildPlaceholderResolver(): PlaceholderResolver = placeholderResolverFn()
    override fun onGameSetup(players: List<Player>) = gameSetupFn(players)
    override fun checkWinCondition(): MatchResult? = winConditionFn()

    override fun onWaitingStart() { builder.onWaitingStart?.invoke() }
    override fun onPlayerJoinWaiting(player: Player) { builder.onPlayerJoinWaiting?.invoke(player) }
    override fun onPlayerLeaveWaiting(player: Player) { builder.onPlayerLeaveWaiting?.invoke(player) }
    override fun onCountdownTick(remaining: Duration) { builder.onCountdownTick?.invoke(remaining) }
    override fun onPlayingStart() { builder.onPlayingStart?.invoke() }
    override fun onPlayerEliminated(player: Player) { builder.onPlayerEliminated?.invoke(player) }
    override fun onPlayerDisconnected(player: Player) { builder.onPlayerDisconnected?.invoke(player) }
    override fun onPlayerReconnected(player: Player) { builder.onPlayerReconnected?.invoke(player) }
    override fun onEndingStart(result: MatchResult) { builder.onEndingStart?.invoke(result) }
    override fun onEndingComplete() { builder.onEndingComplete?.invoke() }
    override fun onGameReset() { builder.onGameReset?.invoke() }
    override fun onPlayerDeath(player: Player, killer: Player?) { builder.onPlayerDeath?.invoke(player, killer) }
    override fun onPlayerRespawn(player: Player) { builder.onPlayerRespawn?.invoke(player) }
    override fun onTeamsAssigned(assignments: Map<String, List<UUID>>) { builder.onTeamsAssigned?.invoke(assignments) }
    override fun onLateJoin(player: Player) { builder.onLateJoin?.invoke(player) }
    override fun onKillStreak(player: Player, streak: Int) { builder.onKillStreak?.invoke(player, streak) }
    override fun onOvertimeStart() { builder.onOvertimeStart?.invoke() }
    override fun onOvertimeEnd() { builder.onOvertimeEnd?.invoke() }
    override fun onAfkEliminated(player: Player) { builder.onAfkEliminated?.invoke(player) }
    override fun onCombatLog(player: Player) { builder.onCombatLog?.invoke(player) }

    override fun onPlayerDamaged(
        victim: Player,
        attacker: Player?,
        amount: Float,
        event: EntityDamageEvent,
    ): Boolean = builder.onPlayerDamaged?.invoke(victim, attacker, amount, event) ?: true

    override fun buildLobbyHotbar(): Hotbar? = builder.buildLobbyHotbar?.invoke() ?: super.buildLobbyHotbar()
    override fun buildRespawnKit(): Kit? = builder.buildRespawnKit?.invoke() ?: super.buildRespawnKit()
    override fun buildRespawnPosition(player: Player): Pos =
        builder.buildRespawnPosition?.invoke(player) ?: super.buildRespawnPosition(player)
    override fun buildChatPipeline(): GameChatPipeline? = builder.buildChatPipeline?.invoke() ?: super.buildChatPipeline()
    override fun buildSpectatorToolkit(): SpectatorToolkit? =
        builder.buildSpectatorToolkit?.invoke() ?: super.buildSpectatorToolkit()
    override fun buildKillFeed(): KillFeed? = builder.buildKillFeed?.invoke() ?: super.buildKillFeed()
    override fun buildDeathRecapTracker(): DeathRecapTracker? =
        builder.buildDeathRecapTracker?.invoke() ?: super.buildDeathRecapTracker()
    override fun buildRewardDistributor(): RewardDistributor? =
        builder.buildRewardDistributor?.invoke() ?: super.buildRewardDistributor()
    override fun buildCeremony(result: MatchResult): Ceremony? =
        builder.buildCeremony?.invoke(result) ?: super.buildCeremony(result)
}

inline fun gameMode(name: String, block: GameModeBuilder.() -> Unit): DslGameMode =
    GameModeBuilder(name).apply(block).build()
