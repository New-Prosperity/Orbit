# Orbit
Standalone Minestom game server for the Nebula network. Runs on every server — hubs and game servers. `java -jar orbit.jar` starts the server with all shared infrastructure. Features are implemented as hot-swappable `OrbitModule`s registered into Ether's `ModuleRegistry`.

## Build
- Plugins: kotlin-jvm 2.3.10, maven-publish, application, shadow 9.0.0-beta12
- Deps: Gravity (implementation), Minestom 2026.02.09-1.21.11, adventure-text-minimessage 4.20.0
- Main class: `me.nebula.orbit.Orbit`
- JVM toolchain: 25 (required by Minestom)
- Gradle wrapper: 9.3.1
- Shadow bundles Gravity/Ether/Hazelcast/Minestom. `mergeServiceFiles()` for Hazelcast Enterprise.

## Entry — `Orbit.kt`
- `object Orbit` with `@JvmStatic fun main()`.
- Env vars: `VELOCITY_SECRET` (required), `SERVER_PORT` (default 25565), `P_SERVER_UUID` (optional), `SERVER_HOST` (optional), `HAZELCAST_ADDRESSES` (optional, comma-separated cluster addresses for client discovery — omit to use Hazelcast auto-discovery).
- **Provision self-discovery**: When `P_SERVER_UUID` is set, Orbit looks up `ProvisionStore.load(serverUuid)` from the distributed Hazelcast store (populated by Pulsar's `ServerSynchronizer.sync()`), deriving `serverName` from the provision's server name and `gameMode` from `provision.metadata["game_mode"]`. No direct Proton API call needed — Orbit relies on the cluster-synced store. When `P_SERVER_UUID` is empty or no provision found, `serverName` defaults to `"orbit-local"` and `gameMode` to `null` (hub mode).
- Hazelcast: **client mode** with 22 stores (includes ReconnectionStore, HostTicketStore, HostRequestStore, HostRequestLookupStore, BattleRoyaleKitStore). Smart client auto-discovers all members after initial connection. Near-cache configs wired automatically by HazelcastModule for stores with `nearCacheSeconds > 0`.
- **Provision metadata**: Extracts `game_mode`, `host_owner` (UUID), and `map` from provision metadata. `Orbit.hostOwner` and `Orbit.mapName` are set when server is host-provisioned.
- **Map loading**: When `gameMode` is set and `STORAGE_URL`/`STORAGE_TOKEN` are provided, loads map via `MapLoader.load()` wrapped in `runCatching` — on failure, logs the error and falls back to the season default world path (null `resolvedWorldPath`). `MapLoader` validates cached maps have a `region/` directory; corrupted caches (missing `region/`) are deleted and re-downloaded. Also initializes `ReplayStorage` with a `"replays"` scope from the same storage client.
- Init order: `environment {}` → `appDelegate` → `app.start()` → provision self-discovery → map loading (with fallback) → `MinecraftServer.init()` → `resolveMode()` → ensure `data/models/` directory → `ModelEngine.install()` → `CustomContentRegistry.init()` (loads items, blocks, armors) → load `.bbmodel` model files → `CustomContentRegistry.mergePack()` (generates armor shaders + HUD shaders + HUD font, merges all) → register HUD test layout → `HudManager.install(handler)` → register commands (basic, game, model, cc, cinematic, screen, armor, hud) → `mode.install(handler)` → common listeners → HUD tick task (2 ticks) → `server.start()` → registration → shutdown hook.
- **Graceful shutdown**: Shutdown hook kicks all connected players ("Server shutting down") before deregistering, so Velocity redirects them to a hub instead of disconnecting.
- **Resource pack**: Pack is merged at startup but NOT sent from Orbit — pack distribution is delegated to the proxy.
- Common listeners: `AsyncPlayerConfigurationEvent` (locale cache, set spawning instance/respawn from mode), `PlayerDisconnectEvent` (evict locale), `OnlinePlayerCache` refresh (5s).
- `resolveMode()` selects `ServerMode` by `gameMode` (sourced from Proton provision `metadata["game_mode"]`, NOT an env var): `null` → `HubMode()`, `"battleroyale"` → `BattleRoyaleMode()`, else → `error()`.

## Server Mode System — `mode/`

### `ServerMode` interface (`mode/ServerMode.kt`)
```kotlin
interface ServerMode {
    val defaultInstance: InstanceContainer
    val spawnPoint: Pos
    val activeInstance: InstanceContainer  // default = defaultInstance
    val activeSpawnPoint: Pos              // default = spawnPoint
    val cosmeticConfig: CosmeticConfig     // default = all enabled
    fun install(handler: GlobalEventHandler)
    fun shutdown()
}
```
Each mode owns its instance, event listeners, scheduled tasks, and shutdown. `Orbit.kt` delegates to it via `mode.activeInstance` / `mode.activeSpawnPoint` for player spawning (in `AsyncPlayerConfigurationEvent`). `cosmeticConfig` has a default implementation returning `CosmeticConfig()` (all categories enabled, empty blacklist) so existing/future modes work without changes. `activeInstance` / `activeSpawnPoint` default to `defaultInstance` / `spawnPoint` — `HubMode` uses the defaults; `GameMode` overrides them for phase-based instance routing.

### Config-Driven Mode System — `mode/config/`

#### Shared Config Data Classes (`mode/config/ModeConfig.kt`)
- `SpawnConfig(x, y, z, yaw, pitch)` — spawn coordinates. `toPos()` converts to Minestom `Pos`.
- `ScoreboardConfig(title, refreshSeconds, lines)` — scoreboard layout. Values are translation keys resolved per-player locale.
- `TabListConfig(refreshSeconds, header, footer)` — tab list layout. Values are translation keys resolved per-player locale.
- `LobbyConfig(gameMode, protectBlocks, disableDamage, disableHunger, lockInventory, voidTeleportY)` — lobby protection settings.
- `LobbyWorldConfig(worldPath, preloadRadius, spawn: SpawnConfig)` — separate lobby world configuration. When present in `GameSettings.lobbyWorld`, enables dual-instance mode (lobby instance + game instance).
- `HotbarItemConfig(slot, material, name, glowing, action)` — hotbar item definition.
- `CosmeticConfig(enabledCategories, blacklist)` — per-mode cosmetic filtering. `enabledCategories` lists allowed `CosmeticCategory` names (default: all five). `blacklist` lists specific cosmetic IDs to block even if category is enabled. Both default to all-enabled / empty for backwards compatibility.

#### Placeholder System (`mode/config/PlaceholderResolver.kt`)
- `{placeholder}` syntax in config strings (curly braces, distinct from MiniMessage `<tag>`).
- `placeholderResolver { global("name") { value }; perPlayer("name") { player -> value } }` DSL.
- `resolve(template, player?)` — replaces `{name}` tokens with provider values.
- `resolveTranslated(key, player)` — translates key per player locale via `TranslationRegistry.get()`, then resolves `{placeholder}` tokens. Falls back to raw string if key is not a registered translation. Used by `GameMode.buildLiveScoreboard()`, `buildLiveTabList()`, and `HubMode`.
- All scoreboard/tablist lines are `LiveLine.Dynamic` — per-player due to locale-dependent translation.

### `HubMode` (`mode/hub/HubMode.kt`)
- **Hardcoded config**: All settings defined in `HubDefinitions.CONFIG` (`HubModeConfig.kt`). No JSON files — config is the Kotlin source of truth.
- **Config data classes**: `HubModeConfig` (`mode/hub/HubModeConfig.kt`) wraps `SpawnConfig`, `ScoreboardConfig`, `TabListConfig`, `LobbyConfig`, `List<HotbarItemConfig>`, `SelectorConfig(title, rows, border)`, `CosmeticConfig`.
- **Default config**: `src/main/resources/hub.json` bundled in JAR.
- **Placeholders**: Global `{online}` (SessionStore.cachedSize), `{server}` (Orbit.serverName). Per-player `{rank}` (PlayerRankStore/RankStore lookup).
- **Action system**: Hotbar items reference click actions by string name (e.g., `"open_selector"`, `"open_host"`). Immutable actions map built locally in `install()`. Unknown actions and invalid material keys logged as warnings.
- **Host system**: `"open_host"` action opens `HostMenu.openGameModeMenu(player)`. Subscribes to `HostProvisionStatusMessage` for real-time player feedback (provisioning/ready/failed). Cleans `HostMenu.pendingPlayers` on READY/FAILED status. Unsubscribes on shutdown.
- **Queue feedback**: Subscribes to `QueuePositionMessage` — sends action bar with position and total queue size every 3s (QueueProcessor interval). Unsubscribes on shutdown.
- **Disconnect cleanup**: `PlayerDisconnectEvent` removes hotbar and clears `HostMenu.removePending(uuid)` to prevent stale pending state.
- **Hub instance**: Validates and loads Anvil world from `config.worldPath` (requires `region/` with `.mca` files), preloads chunks with `config.preloadRadius`, verifies block data post-load. Falls back to flat grass generator if directory missing.
- **Lobby**: Built from `config.lobby` — `GameMode.valueOf(config.lobby.gameMode)`, all protection flags from config.
- **Scoreboard**: Built from `config.scoreboard` — title and lines support `{placeholder}` syntax, static vs dynamic determined by placeholder presence.
- **Tab list**: Built from `config.tabList` — same placeholder resolution for header/footer.
- **Hotbar**: Built from `config.hotbar` — `Material.fromKey()`, maps action strings via registered actions.
- **Server selector**: Built from `config.selector` — title, rows, border material.
- No `MechanicLoader` — hub mode has no mechanics enabled.
- `shutdown()` unsubscribes host status, queue removed, queue assignment, and queue position listeners; uninstalls scoreboard, tab list, lobby, and hotbar.

### Game Engine — `mode/game/`
Abstract lifecycle framework for minigames. Concrete modes subclass `GameMode` and implement only game-specific logic; the base class owns phases, player tracking, countdowns, timers, and cleanup.

#### Phase Lifecycle
```
WAITING ──(minPlayers)──> STARTING ──(countdown)──> PLAYING ──(win / timer)──> ENDING ──(end timer)──> server termination
```
- **WAITING**: Lobby protection, scoreboard/tablist/hotbar. Tracks players. → STARTING when `tracker.aliveCount >= minPlayers`.
- **STARTING**: Countdown (`timing.countdownSeconds`). Cancels → WAITING if players drop below `minPlayers`.
- **PLAYING**: `onGameSetup(players)` for game-specific prep. Grace period + game timer if configured. `eliminate(player)` → spectator, check win condition.
- **ENDING**: `MatchResultDisplay.broadcast()`. End countdown, then terminates the server (`Orbit.app.stop()` + `Runtime.halt(0)` on virtual thread).

#### `GamePhase.kt`
`enum class GamePhase { WAITING, STARTING, PLAYING, ENDING }`

#### `GameSettings.kt`
- `TimingConfig(countdownSeconds, gameDurationSeconds, endingDurationSeconds, gracePeriodSeconds, minPlayers, maxPlayers, allowReconnect, disconnectEliminationSeconds, reconnectWindowSeconds, freezeDuringCountdown, combatLogSeconds, afkEliminationSeconds, minViablePlayers, isolateSpectatorChat, voidDeathY)` — 0 = unlimited/disabled for duration fields. `allowReconnect` (default `true`): when `false`, disconnecting during PLAYING immediately eliminates. `disconnectEliminationSeconds` (default `0`): per-player auto-elimination timer after disconnect. `reconnectWindowSeconds` (default `0`): game-wide reconnect window. `freezeDuringCountdown` (default `false`): prevents XZ movement during STARTING countdown (allows head rotation). `combatLogSeconds` (default `0`): if > 0, players who disconnect within this many seconds of combat are instantly eliminated. `afkEliminationSeconds` (default `0`): if > 0, players with no movement/combat activity for this duration are eliminated. `minViablePlayers` (default `0`): if > 0, game force-ends when `effectiveAliveCount` drops below this threshold. `isolateSpectatorChat` (default `false`): spectator messages only visible to other spectators, alive messages only to alive. `voidDeathY` (default `NEGATIVE_INFINITY`): Y coordinate below which alive players trigger `handleDeath()` automatically.
- `TeamConfig(teamCount, minTeamSize, maxTeamSize, autoBalance, friendlyFire, teamNames)` — optional team mode config. `teamCount` = number of teams. `teamNames` provides display names (falls back to `team_1`, `team_2`, …). `autoBalance` (default `true`): round-robin shuffle assignment. `friendlyFire` (default `false`).
- `RespawnConfig(respawnDelayTicks, maxLives, invincibilityTicks, clearInventoryOnRespawn)` — optional respawn system. `respawnDelayTicks` (default 60): ticks in spectator before respawn. `maxLives` (default 0 = unlimited): when > 0, lives are tracked and exhaustion → elimination. `invincibilityTicks` (default 40): grace period after respawn. `clearInventoryOnRespawn` (default `false`).
- `LateJoinConfig(windowSeconds, joinAsSpectator, maxLateJoiners)` — optional late join window. `windowSeconds` (default 30): time after PLAYING starts where new players can join. `joinAsSpectator` (default `false`): late joiners start as spectator. `maxLateJoiners` (default 0 = unlimited): cap on late join count.
- `OvertimeConfig(durationSeconds, suddenDeath)` — optional overtime when game timer expires without a winner. `durationSeconds` (default 60): overtime duration. `suddenDeath` (default `false`): when true, all deaths during overtime are instant eliminations (bypass respawn).
- `GameSettings(worldPath, preloadRadius, spawn, scoreboard, tabList, lobby, hotbar, timing, cosmetics, teams, respawn, lateJoin, overtime, mapName, lobbyWorld)` — wraps all config types. Optional fields default to `null` (disabled). `cosmetics` defaults to `CosmeticConfig()` (all enabled). `lobbyWorld` (default `null`): when set, enables dual-instance mode — lobby world for WAITING/STARTING, game world for PLAYING/ENDING.

#### `PlayerTracker.kt`
- `sealed interface PlayerState { Alive, Spectating, Respawning, Disconnected(since, wasRespawning) }`
- `PlayerTracker` — `ConcurrentHashMap<UUID, PlayerState>` with full team, lives, and stats tracking.
  - **State properties**: `alive`, `spectating`, `disconnected`, `respawning`, `all` (Set<UUID>). `aliveCount`, `effectiveAliveCount` (non-spectators), `size`.
  - **State methods**: `join`, `eliminate`, `revive`, `markRespawning`, `disconnect`, `reconnect`, `remove` (cleans all 12 maps), `stateOf`, `isAlive`, `isSpectating`, `isDisconnected`, `isRespawning`, `isActive`, `contains`, `clear`.
  - **Team methods**: `assignTeam`, `teamOf`, `teamMembers`, `aliveInTeam`, `activeInTeam`, `isTeamEliminated`, `aliveTeams`, `allTeams`, `teamSizes`, `areTeammates`.
  - **Lives methods**: `setLives`, `livesOf`, `decrementLives`, `hasLivesRemaining`.
  - **Stats methods**: `recordKill`, `recordDeath`, `killsOf`, `deathsOf`. Kill also increments streak; death resets streak to 0.
  - **Streak methods**: `streakOf(uuid)` — current consecutive kill count (reset on death).
  - **Assist methods**: `recordAssist`, `assistsOf`.
  - **Damage tracking**: `recordDamage(attacker, victim)` — records timestamped damage for assist credit and combat log detection. `recentDamagersOf(victim, windowMillis)` returns attackers within window. `DamageRecord(attacker, timestamp)`.
  - **Combat tracking**: `lastCombatTimeOf`, `isInCombat(uuid, windowMillis)` — tracks last combat timestamp per player.
  - **Activity tracking**: `markActivity(uuid)`, `lastActivityOf`, `isAfk(uuid, thresholdMillis)` — tracks last movement/action timestamp for AFK detection.
  - **Score tracking**: `addScore(uuid, amount)`, `scoreOf(uuid)`, `teamScoreOf(team)`, `scoreboard(): List<Pair<UUID, Double>>`, `teamScoreboard(): List<Pair<String, Double>>` — generic per-player score system for objective-based games.
  - **Elimination placement**: `eliminationOrderOf(uuid)` — ordinal elimination order (1 = first eliminated). `placementOf(uuid, totalPlayers)` — placement (1 = winner). `eliminationOrderedList()` — all eliminated UUIDs in elimination order. Tracked automatically on `eliminate()`.

#### `GameMode.kt`
Abstract `ServerMode` implementation. Fields: `spawnPoint` (lazy), `gameInstance` (mutable backing, lazily created via `createGameInstance()`), `lobbyInstance` (lazy, falls back to `gameInstance` when `lobbyWorld` is null), `resolver` (lazy), `stateMachine` (lazy). All mutable `var` fields accessed across scheduler tasks and event handlers are `@Volatile` for cross-thread visibility (task refs, countdown/timer refs, event nodes, pipeline refs, game instance).

**Dual-instance mode** (when `settings.lobbyWorld != null`):
- `isDualInstance: Boolean` — true when `lobbyWorld` is configured.
- `lobbyInstance` — separate `InstanceContainer` loaded from `lobbyWorld.worldPath`. Defaults to `gameInstance` when `lobbyWorld` is null (single-instance backward compat).
- `lobbySpawnPoint` — from `lobbyWorld.spawn`, falls back to `spawnPoint`.
- `gameInstance` — the actual game world (from `settings.worldPath` or overridden by subclass via `createGameInstance()`). Mutable backing allows recreation between rounds via `invalidateGameInstance()`.
- `activeInstance` — returns `lobbyInstance` during WAITING/STARTING, `gameInstance` during PLAYING/ENDING. Used by `Orbit.kt` for player spawning.
- `activeSpawnPoint` — returns `lobbySpawnPoint` during WAITING/STARTING, `spawnPoint` during PLAYING/ENDING.
- `createGameInstance()` — open, override in subclass for custom instance creation (e.g. procedural generation). Base loads from `settings.worldPath` via `AnvilWorldLoader`.
- `invalidateGameInstance()` — transfers remaining players to lobby, unregisters old instance. Called by subclass in `onGameReset()` for round-based regeneration.
- Player transfer: `enterPlaying()` transfers all alive players from `lobbyInstance` → `gameInstance` (blocking `CompletableFuture.allOf`). `enterWaiting()` transfers all players from `gameInstance` → `lobbyInstance`.
- **Replay recording**: `ReplayRecorder` field in `GameMode` base. `enterPlaying()` calls `replayRecorder.start()`, `enterEnding()` calls `replayRecorder.stop()` and persists via `ReplayStorage.save()` on a virtual thread if `ReplayStorage.isInitialized()`. Replay key: `"${serverName}-${timestamp}"`.

**Composed utilities** (managed by base):

| Utility | Phase | Notes |
|---|---|---|
| `Lobby` | WAITING | From `settings.lobby`, installed on `lobbyInstance`. Created on WAITING enter, uninstalled on PLAYING enter |
| `Hotbar` | WAITING | Via `buildLobbyHotbar()` (open, null default). Uninstalled on PLAYING enter |
| `LiveScoreboard` | All | Built once in `install()` from `settings.scoreboard` + resolver |
| `LiveTabList` | All | Built once in `install()` from `settings.tabList` + resolver |
| `Countdown` | STARTING | `timing.countdownSeconds`. Cancels → WAITING if players drop |
| `MinigameTimer` | PLAYING | `timing.gameDurationSeconds`. 0 = not created. Expiry → ENDING |
| `GracePeriodManager` | PLAYING start | `timing.gracePeriodSeconds`. 0 = skipped. Also manages respawn invincibility |
| `MatchResultDisplay` | ENDING | Broadcasts result to all instance players |
| `MatchResultManager` | ENDING | Stores result in local history |
| `Countdown` | ENDING | `timing.endingDurationSeconds`. Complete → server termination |

**Abstract** (must implement): `settings: GameSettings`, `buildPlaceholderResolver()`, `onGameSetup(players)`, `checkWinCondition(): MatchResult?`.

**Open hooks** (default no-op):
- Phase hooks: `onWaitingStart()`, `onPlayingStart()`, `onEndingStart(result)`, `onEndingComplete()`, `onGameReset()`.
- Player hooks: `onPlayerJoinWaiting(player)`, `onPlayerLeaveWaiting(player)`, `onPlayerEliminated(player)`, `onPlayerDisconnected(player)`, `onPlayerReconnected(player)`, `onPlayerDeath(player, killer?)`, `onPlayerRespawn(player)`, `onLateJoin(player)`.
- Team hook: `onTeamsAssigned(assignments: Map<String, List<UUID>>)`.
- `onCountdownTick(remaining)`.
- `onAllPlayersEliminated()` — default: `forceEnd(draw)`.
- `onKillStreak(player, streak)` — called when a player gets 2+ consecutive kills without dying.
- `onOvertimeStart()`, `onOvertimeEnd()` — called when overtime begins/ends.
- `onAfkEliminated(player)` — called before AFK player is eliminated.
- `onCombatLog(player)` — called when a player disconnects during combat and is eliminated.

**Open builders** (override to customize):
- `buildLobbyHotbar(): Hotbar?` — lobby hotbar, null = none.
- `buildRespawnKit(): Kit?` — kit applied on respawn, null = none.
- `buildRespawnPosition(player): Pos` — respawn location, default = `spawnPoint`.
- `resolveGameDuration(): Int` — game timer duration in seconds, default = `settings.timing.gameDurationSeconds`. Override to apply voted/dynamic duration. Called after `onGameSetup()`.
- `buildTimeExpiredResult(): MatchResult` — result when game timer expires, default = draw.
- `buildOvertimeResult(): MatchResult` — result when overtime expires without a winner, default = draw.
- `assignTeams(players): Map<UUID, String>` — team assignment algorithm, default = shuffled round-robin.
- `persistGameStats(result)` — save stats externally on game end.

**Damage hook**: `onPlayerDamaged(victim, attacker?, amount, event): Boolean` — open method called for every damage event during PLAYING (after friendly fire check). Return `false` to cancel. Runs inside unified `gamemode-mechanics` event node. Subclasses use this instead of creating their own damage listeners for game-wide damage logic.

**Public API**: `eliminate(player)`, `revive(player, pos)`, `handleDeath(player, killer?)`, `forceReconnect(player)`, `forceStart()`, `forceEnd(result)`, `phase: GamePhase`, `tracker: PlayerTracker`, `gameStartTime: Long`, `initialPlayerCount: Int`, `isTeamMode: Boolean`, `isFriendlyFireEnabled: Boolean`, `isOvertime: Boolean`, `isSuddenDeath: Boolean`, `areTeammates(a, b): Boolean`.

**Spectator cycling**: `nextSpectatorTarget(player): Player?`, `previousSpectatorTarget(player): Player?` — cycles through alive players. In team mode, prioritizes teammates first, falls back to all alive. Tracks current target per spectator in internal map.

**Scheduled game events**: `scheduleGameEvent(name, delayTicks, action): Task` — schedule a named event during PLAYING that auto-cancels on phase change. `cancelGameEvent(name)` cancels by name. All events cleaned up on ENDING/WAITING transitions.

**Broadcast helpers**: `broadcastAlive(action)`, `broadcastSpectators(action)`, `broadcastTeam(team, action)`, `broadcastAll(action)` — iterate online players by state/team with a lambda.

**Protected helpers**: `lastTeamStandingName(): String?`, `lastPlayerStandingUuid(): UUID?`.

**Team system** (enabled when `settings.teams != null`):
- Teams assigned in `enterPlaying()` via `assignTeams()` (shuffled round-robin by default). Override for custom logic.
- Late joiners auto-assigned to smallest team.
- `areTeammates(a, b)`, `isTeamMode`, `isFriendlyFireEnabled` for game-specific logic.
- `lastTeamStandingName()` returns winning team when only one active team remains.

**Respawn system** (enabled when `settings.respawn != null`):
- `handleDeath(player, killer?)` records stats, decrements lives, schedules respawn or eliminates.
- Respawning players enter SPECTATOR for `respawnDelayTicks`, then revive at `buildRespawnPosition()` with optional `buildRespawnKit()`.
- Invincibility grace period after respawn (`invincibilityTicks`).
- If respawn timer fires while player is offline: transitions to Disconnected state with reconnection data.
- If player reconnects while was respawning: immediately respawns at `buildRespawnPosition()`.

**Late join system** (enabled when `settings.lateJoin != null`):
- Window starts on PLAYING enter, expires after `windowSeconds`.
- New players join via `canLateJoin()` check (window active + max not reached).
- Late joiners receive team assignment (smallest team), lives initialization, and `onLateJoin()` hook.
- `joinAsSpectator` option for observation-first late join.

**Reconnection system** (from `TimingConfig`):
- `allowReconnect`: disconnected players keep their state, saved to `ReconnectionStore`.
- `disconnectEliminationSeconds`: per-player auto-elimination timer.
- `reconnectWindowSeconds`: game-wide window; after expiry, all disconnected are eliminated and new disconnects are instant.
- Reconnecting players restore previous state (Alive or trigger respawn if was Respawning).

**Overtime system** (enabled when `settings.overtime != null`):
- Triggered when game timer expires but `checkWinCondition()` returns null and players remain.
- `durationSeconds` overtime period with `onOvertimeStart()` / `onOvertimeEnd()` hooks.
- `suddenDeath` mode: all deaths during overtime bypass respawn → instant elimination.
- `isOvertime` / `isSuddenDeath` read-only properties for game-specific logic.
- `buildOvertimeResult()` used when overtime expires without a winner.

**AFK detection** (enabled when `timing.afkEliminationSeconds > 0`):
- Tracks last activity timestamp per player via `PlayerMoveEvent` listener.
- Checks every 5 seconds (100 ticks). Players exceeding threshold are eliminated via `onAfkEliminated()` hook.
- Activity resets on join, reconnect, movement.

**Combat log detection** (enabled when `timing.combatLogSeconds > 0`):
- Tracks last combat timestamp per player via `PlayerTracker.recordDamage()`.
- Players disconnecting within the combat window are instantly eliminated regardless of reconnect settings.
- `onCombatLog(player)` hook for announcements.

**Countdown freeze** (enabled when `timing.freezeDuringCountdown = true`):
- Installs `PlayerMoveEvent` listener during STARTING phase that locks XZ position.
- Allows head rotation (yaw/pitch pass through).
- Event node automatically removed on countdown complete or phase change.

**Kill streaks**:
- `PlayerTracker` automatically tracks consecutive kills per player (reset on death).
- `onKillStreak(player, streak)` hook fired when streak ≥ 2 after each kill.

**Assist tracking**:
- `PlayerTracker.recordDamage(attacker, victim)` records timestamped damage events.
- On death, `creditAssists()` scans recent damagers within 10s window, credits assists to all except killer and self.
- `tracker.assistsOf(uuid)` for stat display.

**Minimum viable players** (enabled when `timing.minViablePlayers > 0`):
- `checkGameEnd()` force-ends game when `effectiveAliveCount` drops below threshold.
- Prevents zombie games where only 1 player remains in an FFA that requires 2+.

**Friendly fire blocking** (automatic when `teamConfig.friendlyFire == false`):
- Built-in `EntityDamageEvent` listener cancels damage between teammates.
- Part of unified `gamemode-mechanics` event node — no per-game reimplementation needed.

**Auto-spectate on elimination**:
- When eliminated, player auto-spectates their last attacker (if alive) or nearest alive player.
- Uses existing spectator target tracking. Seamlessly integrates with `nextSpectatorTarget()` / `previousSpectatorTarget()` cycling.

**Elimination placement tracking**:
- `PlayerTracker.eliminate()` records ordinal elimination order via `AtomicInteger`.
- `tracker.placementOf(uuid, totalPlayers)` — 1 = winner, N = first eliminated.
- `tracker.eliminationOrderedList()` for full elimination history.
- `initialPlayerCount` stored at PLAYING enter for placement calculation.

**Score tracking**:
- Generic per-player score via `tracker.addScore(uuid, amount)`, `tracker.scoreOf(uuid)`.
- Per-team aggregation: `tracker.teamScoreOf(team)`.
- Sorted leaderboards: `tracker.scoreboard()`, `tracker.teamScoreboard()`.
- Supports any scoring model (kills, objectives, captures, etc.).

**Spectator chat isolation** (enabled when `timing.isolateSpectatorChat = true`):
- `PlayerChatEvent` listener filters recipients: spectator messages only visible to other spectators, alive messages only to alive players.
- Prevents ghosting and information leaking from eliminated players.

**Void death handling** (enabled when `timing.voidDeathY != NEGATIVE_INFINITY`):
- Repeating task (every 10 ticks) checks if alive players are below the Y threshold.
- Skips players who are currently respawning to prevent killing players mid-respawn.
- Triggers `handleDeath(player)` — integrates with respawn system, lives, kill streaks, etc.
- Cleaned up on phase transitions.

**Unified game mechanics node** (`gamemode-mechanics`):
- Single `EventNode` consolidating friendly fire blocking, damage hook, damage tracking, activity tracking, and spectator chat isolation.
- Installed at PLAYING enter, removed at ENDING/WAITING. Avoids multiple overlapping event nodes.

**Edge case handling**:
- Double-eliminate protection: `eliminate()` checks `isAlive || isRespawning` before proceeding.
- Max player enforcement in WAITING and STARTING: excess players become spectators.
- `forceEnd()` guards against ENDING/WAITING phases.
- `checkGameEnd()` called after every elimination/disconnect, checks `checkWinCondition()`, `minViablePlayers`, and `effectiveAliveCount == 0`.
- Respawn timer cancellation on disconnect/elimination.
- Full cleanup on phase transitions: reconnection state, respawn timers, late join state, game events, AFK check, overtime, freeze node, game mechanics node, void check.
- Spectator target tracking cleaned up on revive, respawn, disconnect, and game reset.
- `stopSpectating()` called before revive/respawn to ensure clean camera state.

**Host ticket consumption**: In `enterPlaying()`, if `Orbit.hostOwner` is set, atomically consumes one ticket via `HostTicketStore.executeOnKey(owner, ConsumeTicketProcessor())`. Skipped for admins (`RankManager.hasPermission(owner, "*")`). Ticket is only consumed when the game enters PLAYING phase — failed provisions or server crashes during WAITING/STARTING don't waste tickets.

### `HostMenu` (`mode/hub/HostMenu.kt`)
GUI flow for hosting a game server. Three-step menu: gamemode selection → map selection (skipped if ≤1 map) → confirmation.

- **Admin bypass**: All ticket checks (`openGameModeMenu`, gamemode click, confirm) respect `RankManager.hasPermission(uuid, "*")` — admins can host without tickets.
- **Gamemode menu**: Lists all `PoolConfig` entries where `hostable == true`. Shows ticket count. Shows error message if no hostable modes exist.
- **Map menu**: Lists `PoolConfig.maps` for selected gamemode. Skipped if 0-1 maps.
- **Confirm menu**: Shows selected gamemode/map and cost (1 ticket). Re-validates tickets on click (fresh load from store).
- **On confirm**: Closes inventory first, publishes `HostProvisionRequestMessage` via `NetworkMessenger` with host owner, gamemode, map, and party members.
- **Anti-double-click**: `pendingPlayers: ConcurrentHashMap.newKeySet()` — `confirm()` calls `pendingPlayers.add()` which returns false if already pending. Cleared on READY/FAILED status or player disconnect via `removePending(uuid)`.
- **Duplicate prevention**: Checks `HostRequestLookupStore.exists(player.uuid)` before publishing.
- **Party integration**: `collectMembers()` resolves party via `PartyLookupStore` → `PartyStore`, falls back to solo.
- **Translation keys**: `orbit.host.*` (22 keys for menu titles, items, errors, status messages).

### `BattleRoyaleMode` (`mode/game/battleroyale/BattleRoyaleMode.kt`)
FFA last-player-standing battle royale with kit system, legendary weapons, golden heads, configurable spawn modes (ring/random/battle bus), multi-phase border, deathmatch, and optional procedural map generation.

- **Season-driven config**: All BR settings (world, spawn, scoreboard, tablist, lobby, timing, border, spawn mode, golden head, deathmatch, cosmetics, map preset, lobby world) are defined in the `Season` data class and configured via the Season DSL. `BattleRoyaleMode` reads from `SeasonConfig.current`. No JSON config files.
- **Config data classes** (`BattleRoyaleConfig.kt`):
  - `BorderConfig(initialDiameter, finalDiameter, centerX, centerZ, shrinkStartSeconds, shrinkDurationSeconds)` — legacy single-phase border.
  - `BorderPhaseConfig(startAfterSeconds, targetDiameter, shrinkDurationSeconds, damagePerSecond)` — multi-phase progressive border shrink with escalating damage.
  - `SpawnModeConfig(mode, ringRadius, extendedRingRadius, busHeight, busSpeed, parachuteDurationTicks, randomMinDistance)` — spawn placement strategy.
  - `GoldenHeadConfig(enabled, healAmount, absorptionHearts, regenDurationTicks, regenAmplifier)` — golden head healing parameters.
  - `DeathmatchConfig(enabled, triggerAtPlayers, teleportToCenter, borderDiameter, borderShrinkSeconds)` — endgame deathmatch trigger.
  - `KitDefinitionConfig(id, nameKey, descriptionKey, material, locked, maxLevel, xpPerLevel, tiers: Map<Int, KitTierConfig>)` — per-kit definitions with leveled tier progression.
  - `KitTierConfig(helmet?, chestplate?, leggings?, boots?, items)` — gear at a specific kit level.
  - `StarterKitConfig(helmet?, chestplate?, leggings?, boots?, items)` — default gear when no kit selected.
- **Map presets**: `mapPreset: String?` field in `Season` references a programmatic `MapPresets[name]` entry. `"perfect"` / `"battleroyale"` preset: seed-based randomized map (radius 200-300, sea level 60-65, cave/ore/population variance) with all custom biomes.
- **Placeholders**: Global `{online}`, `{server}`, `{alive}`, `{phase}`, `{deathmatch}`. Per-player `{kills}`.

#### Season System (`SeasonConfig.kt`, `SeasonDsl.kt`, `seasons/`)
All BR config — world, gameplay, scoring, and seasonal content (kits, XP rates, starter kit, vote categories, loot tables) — defined via DSL in per-season files under `seasons/`. `SeasonConfig.current` points to the active season. To start a new season: create `seasons/Season2.kt` with `fun season2() = season(2) { ... }` and update `SeasonConfig.current`.

- **Season DSL** (`SeasonDsl.kt`): `season(id) { xp(); starterKit {}; kit(id) {}; vote(id) {}; lootTable(name) {}; world(); spawn(); scoreboard(); tabList(); lobby(); timing(); border(); borderPhase(); borderDamage(); spawnMode(); goldenHead(); deathmatch(); cosmetics(); mapPreset(); lobbyWorld() }`. Builders: `SeasonBuilder`, `KitDefBuilder`, `EquipmentBuilder`, `VoteCategoryBuilder`. `@SeasonDslMarker` annotation for scope control.
- **EquipmentBuilder**: Shared by kit tiers and starter kit. `armor("iron")` expands to 4 armor piece keys. Individual `helmet()`, `chestplate()`, `leggings()`, `boots()` overrides. `item(slot, key, amount)` for inventory items.
- **Kit DSL**: `name(baseKey)` sets both `nameKey = "$baseKey.name"` and `descriptionKey = "$baseKey.desc"`. `icon(material)`, `unlocked()`/`locked()`, `levels(max, xp)`.
- **Season data preservation**: `BattleRoyaleKitStore` key is `"$season:$uuid"` (via `kitKey(season, uuid)`). Each season gets independent player entries. Old season entries stay in SQL forever — no data loss on season transition.
- **XP rewards**: `Map<String, Long>` — string-keyed, extensible per season. Standard keys: `"kill"`, `"win"`, `"survival"`. New sources can be added per season without code changes (e.g., `"assist"`, `"objective"`).
- **Vote categories**: `List<VoteCategoryDef>` — season-scoped. Each category has `id`, `nameKey`, `material`, `defaultIndex`, and `List<VoteOptionDef>`. Different seasons can define different vote categories/options.
- **Loot tables**: `List<ChestLootTable>` — season-scoped. Defined via `lootTable(name) { ... }` in the season DSL. Registered in `ChestLootManager` on `BattleRoyaleMode.init`, cleared on `onGameReset()`. Supports custom content items via `ItemResolver`.
- **Season 1** (`seasons/Season1.kt`): 6 kits (warrior, archer unlocked; tank, scout, alchemist, berserker locked), kill/win/survival XP (50/200/25), wooden sword + pickaxe starter kit, 3 vote categories (duration/health/border).

#### Spawn Mode System (`SpawnMode.kt`)
Auto-managed player spawn placement. No manual `spawnPoints` in config — `SpawnModeExecutor` handles all positioning based on the configured mode.

- **Modes** (`SpawnMode` enum):
  - `HUNGER_GAMES` — N equally-spaced positions on a circle of `ringRadius` (default 80) around center. Players face center.
  - `EXTENDED_HUNGER_GAMES` — Same as HUNGER_GAMES but uses `extendedRingRadius` (default 200) for wider spread.
  - `RANDOM` — Random positions within `mapRadius`, enforcing `randomMinDistance` (default 20) between players. 200 attempts per player with fallback.
  - `BATTLE_ROYALE` — Invisible ArmorStand "bus" moves across the map at `busHeight` (default 150) with `busSpeed` (default 1.5). Players mount the bus, press shift to dismount. On dismount: `SLOW_FALLING` potion for `parachuteDurationTicks` (default 400). Auto-ejects remaining players when bus reaches the end. PvP blocked while bus is active.
- **Sneak detection**: Uses `PlayerPacketEvent` + `ClientInputPacket.shift()` (Minestom has no high-level sneak event).
- **SpawnModeResult**: Tracks `pvpBlocked`, `busEntity`, `busTask`, `dismountNode`, `ejectedPlayers`. Cleaned up in `onGameReset()` via `SpawnModeExecutor.cleanup()`.
- **Integration**: `BattleRoyaleMode.onGameSetup()` calls `SpawnModeExecutor.execute()` with the config, player list, instance, center position, and map radius. The `onPlayerReady` callback handles teleport, gamemode, health, and kit application per player.

#### Kit System (`BattleRoyaleKit.kt`)
- **Gravity store**: `BattleRoyaleKitStore` (`gravity/battleroyale/BattleRoyaleKitStore.kt`) — SQL-backed `Store<String, BattleRoyaleKitData>` with write-behind 15s, DISCARD merge. Key format: `"$season:$uuid"`.
  - `kitKey(season, uuid): String` — composite key builder. `kitKeyUuid(key): UUID` — extracts UUID from key.
  - `BattleRoyaleKitData(selectedKit, kitXp: Map<String, Long>, kitLevels: Map<String, Int>, unlockedKits: Set<String>)`.
  - `SelectKitProcessor(kitId)` — validates kit is unlocked before selecting.
  - `AwardKitXpProcessor(kitId, amount, xpPerLevel)` — atomic XP award with level-up detection, returns `XpAwardResult(newXp, newLevel, leveledUp)`.
  - `UnlockKitProcessor(kitId)` — adds to unlocked set, initializes level to 1.
- **BattleRoyaleKitManager**: Reads kit definitions and XP rates from `SeasonConfig.current`. GUI menu in lobby via hotbar slot 3 (`Material.NETHER_STAR`).
  - Shows locked/unlocked/selected status with level/XP progress.
  - Tier resolution: finds highest tier ≤ player level.
  - Item resolution via `ItemResolver` — material strings in `KitTierConfig` and `StarterKitConfig` can be custom content IDs (e.g., `"ruby_sword"`) or vanilla keys (e.g., `"minecraft:iron_sword"`).
  - XP awarded via `awardXp(player, source)` with string-keyed sources resolved from `season.xpRewards` map.
  - Starter kit from season used when no kit selected.
  - Level-up broadcast to player.

#### Legendary Weapon System (`LegendaryWeapon.kt`)
Framework-only — actual weapons designed separately.
- `LegendaryAbility` interface: `onActivate(player)`, `onPassiveHit(player, target, damage)`, `onPassiveHurt(player, attacker, damage)`, `onKill(player, victim)`.
- `LegendaryDefinition(id, nameKey, baseMaterial, ability, cooldown, cooldownIndicator)`.
- `LegendaryRegistry`: register/unregister/createStack/identifyLegendary with `Tag.String("br_legendary_id")`.
- `LegendaryListener`: `PlayerUseItemEvent` for activation (integrated with `SkillCooldown`), `EntityDamageEvent` for passive hit/hurt callbacks, `notifyKill(killer, victim)` for kill callbacks.

#### Golden Head System (`GoldenHead.kt`)
- `GoldenHeadManager`: creates golden apple items with `Tag.Boolean("br_golden_head")`.
- On consume: heals, applies absorption + regeneration effects.
- Dropped by killed players to their killer.

#### Vote System (`BattleRoyaleVoteManager.kt`)
Pre-game voting during WAITING phase. Players vote on game settings via GUI hotbar item (slot 5). Categories and options are season-defined (`VoteCategoryDef` / `VoteOptionDef`), not hardcoded enums.
- **Categories**: Read from `SeasonConfig.current.voteCategories`. Each has `id`, `nameKey`, `material`, `defaultIndex`, and a list of `VoteOptionDef(nameKey, material, value)`.
- **Resolution**: `resolve(categoryId)` returns winning option index (majority wins). `resolveValue(categoryId)` returns the numeric value. `resolveOptionName(player, categoryId)` returns translated name.
- **GUI**: `openCategoryMenu(player)` — dynamically centered layout based on category count. `openOptionMenu(player, categoryId)` — same centering for options, back button at slot 22.
- **Lifecycle**: Votes cast during WAITING/STARTING. Resolved in `onGameSetup()` into `votedValues: Map<String, Int>`, results broadcast to all players, then cleared.
- **Application**: `votedValues["duration"]` → overrides `resolveGameDuration()` (base GameMode hook). `votedValues["health"]` → sets `MAX_HEALTH` attribute + heals. `votedValues["border"]` → multiplies border phase durations (0.6x fast, 1.5x slow).

#### Gameplay
- **WAITING**: Lobby protection + kit selection hotbar (slot 3, static Book item) + vote settings hotbar (slot 5).
- **STARTING**: 15s countdown with freeze during countdown.
- **PLAYING**:
  - Vote resolution: game settings (duration, health, border speed) applied from vote results.
  - Spawn mode execution: `SpawnModeExecutor.execute()` places players per configured mode (ring/random/bus). BATTLE_ROYALE mode blocks PvP while bus is active.
  - Kit application: `BattleRoyaleKitManager.resolveKit(player)` applies tier-appropriate gear.
  - Health override: voted health applied via `MAX_HEALTH` attribute modification.
  - Grace period: 30s PvP protection (managed by base `GracePeriodManager`).
  - Border: multi-phase progressive shrink with escalating damage (1→2→3 per second), speed scaled by vote multiplier. Falls back to legacy single-phase if `borderPhases` empty.
  - Border damage: 1-second tick checks all alive players outside border, applies `DamageType.OUT_OF_WORLD`.
  - Kill tracking: `EntityDamageEvent` via `EventNode.all()`. Tags target with last attacker UUID + timestamp (10s expiry). On lethal damage: cancels event, heals, credits killer, awards XP, drops golden head, sends death recap, does kill cam (spectate killer for 3s), calls `eliminate()`.
  - Death recap: `brDeathRecapTracker` records all damage in `onPlayerDamaged()`. On lethal hit: `sendRecap(victim)` displays damage summary, then victim spectates killer for 60 ticks (kill cam). Cleared per-player on death and globally on `onGameReset()`.
  - Deathmatch: triggers when alive count ≤ `triggerAtPlayers`. Teleports all alive to center (uses generated map center when available), shrinks border to `borderDiameter`.
  - Elimination: awards survival XP to eliminated player, broadcasts translated message. Triggers deathmatch check.
  - Win condition: `tracker.aliveCount <= 1` → winner is last alive, wins XP awarded. On timer expiry: top killer wins.
- **ENDING**: `MatchResult` with winner, kill stats, game duration.
- **Reconnection**: disabled (`allowReconnect = false`).
- **Translation keys**: `orbit.game.br.*` (elimination, border, deathmatch, golden head, kit selection/level/XP, vote categories/options/results).
- **Activation**: `resolveMode()` in `Orbit.kt` maps `"battleroyale"` → `BattleRoyaleMode()`. Set via Proton provision `metadata["game_mode"] = "battleroyale"`.

#### Procedural Map Generation (`utils/mapgen/`)
Optional system activated when `mapGeneration` is non-null in config. Overrides `defaultInstance` and `spawnPoint` with procedurally generated terrain.

- **Noise** (`Noise.kt`): Foundation noise library.
  - `PerlinNoise(seed)` — classic 2D/3D Perlin noise with permutation table.
  - `OctaveNoise(base, octaves, lacunarity, persistence)` — fractal Brownian motion with normalization.
  - `RidgedNoise(base, octaves, lacunarity, gain, offset)` — ridge-pattern noise for mountains/ridges.
  - `WarpedNoise(primary, warpX, warpZ, warpStrength)` — domain warping for organic terrain.
  - `NoiseSource2D` interface + `.asSource()` extensions for composition.
  - `ScaledNoise(source, scaleX, scaleZ)` — coordinate scaling wrapper.
- **Biomes** (`Biome.kt`): Full biome system with 16 default biomes, zones, exclusion, blending, Minestom registry integration.
  - `BiomeDefinition` — 21 fields: id, surfaceBlock, fillerBlock, underwaterSurface, stoneBlock, baseHeight, heightVariation, heightCurve, temperature, moisture, treeDensity, vegetationDensity, treeTypes, caveFrequencyMultiplier, oreMultiplier, snowLine, frozen, hasPrecipitation, waterColor, grassColor, foliageColor, grassModifier.
  - `GrassModifier` enum: NONE, DARK_FOREST, SWAMP — maps to `BiomeEffects.GrassColorModifier` for client-side grass shading.
  - `BiomeRegistry` object — `register`/`get`/`require`/`all`/`clear`/`registerDefaults`/`registerMinestomBiomes`/`getRegistryKey`. 16 defaults with vanilla-accurate visual colors.
  - `registerMinestomBiomes()` — registers all `BiomeDefinition`s as real Minestom `Biome` objects (namespace `nebula:<id>`). Sets waterColor, grassColor, foliageColor, grassColorModifier, temperature, downfall, precipitation. Custom biome names appear in F3 (e.g., `nebula:swamp`).
  - `BiomeZoneConfig` — centerBiomeId, centerRadius, ringBiomeId, ringRadius, zoneShape (CIRCLE/SQUARE), excludedBiomeIds, fallbackBiomeId, biomeScale, blendRadius.
  - `BiomeProvider(seed, config)` — temperature+moisture+weirdness noise selection, center/ring zone override, exclusion filtering, `blendedHeight()` for smooth biome transitions via cubic hermite interpolation (dense sampling at half-radius steps, smooth falloff with zero-derivative at boundaries).
  - `HeightCurve` enum: LINEAR, SMOOTH, TERRACE, AMPLIFIED, CLIFF, RIDGED (sharp ridgelines from ridged noise), MESA (flat tops with sheer cliff edges), ROLLING (double-smoothstep ultra-smooth).
  - `TreeType` enum: OAK, BIRCH, SPRUCE, DARK_OAK, ACACIA, JUNGLE.
- **Terrain** (`TerrainGenerator.kt`): Minestom `Generator` with multi-layer noise.
  - `TerrainConfig` — seed, seaLevel, bedrockHeight, fillerDepth, deepslateLevel, beachesEnabled, terrainScale, continentalScale/Influence, erosionScale/Strength, river params (scale/threshold/depth/width), overhang params (scale/threshold), biomeZones.
  - 7 noise layers: height, detail, continental, erosion, river, overhang, ridged.
  - Per column: blended height via BiomeProvider, river detection, bedrock→deepslate→stone→filler→surface layering, water fill (ICE in frozen biomes), 3D overhang density above surface, snow placement at snowLine, `modifier.setBiome()` per 4-block Y interval using registered `RegistryKey<Biome>`.
  - Beach generation: sand surface when height within ±2 of sea level, excluded in desert/badlands/snowy_plains/ice_spikes/swamp/jungle.
  - Badlands terracotta bands: Y-dependent colored terracotta (orange/yellow/brown/red/white/light_gray in 12-block cycle).
  - Deepslate layer below configurable deepslateLevel (default 8).
  - Heightmap cache: `ConcurrentHashMap<Long, Int>` via `surfaceHeight(x, z)` with `computeIfAbsent`.
  - Height curves: smoothstep, terrace (floor-stepped), amplified (ridged blend), cliff (squared positive).
  - Rivers: carved where `abs(noise) < threshold`, 4-block deep channels.
- **Caves** (`CaveGenerator.kt`): Dedicated cave carving system.
  - `CaveConfig` — worm params (frequency, length range, radius range, Y range), ravine params (frequency, length range, radius range, vertical stretch), room params (frequency, radius range, Y range), lavaLevel, bedrockFloor, decorationEnabled, mossEnabled, glowLichenEnabled, dripstoneEnabled, hangingRootsEnabled, aquifersEnabled, aquiferMaxY, aquiferThreshold. Presets: `vanilla()`, `dense()`, `sparse()`, `none()`.
  - `CaveCarver(seed, config, terrain)` — Perlin worm cave algorithm: walks forward in 3D with noise-guided direction, sinusoidal radius variation, carves spheres. Ravines use vertically-stretched ellipsoid cross sections. Rooms are large spherical caverns with vertical squash. Lava below lavaLevel. Won't carve into water from below.
  - `decorateAll(instance, radiusChunks)` — post-carving decoration pass. Scans CAVE_AIR blocks and decorates:
    - Aquifers: noise-driven water on cave floors below `aquiferMaxY`.
    - Moss: 12% floor chance → MOSS_BLOCK + 50% MOSS_CARPET.
    - Ceiling dripstone: 4% → POINTED_DRIPSTONE (down), 40% DRIPSTONE_BLOCK above.
    - Hanging roots: 3% above Y=45.
    - Floor stalagmites: 3% → POINTED_DRIPSTONE (up), 30% DRIPSTONE_BLOCK below.
    - Glow lichen: 3% on walls adjacent to solid blocks.
- **Ores** (`OreGenerator.kt`): Per-ore vein generation.
  - `OreVeinConfig(block, minY, maxY, veinSize, veinsPerChunk, multiplier)` — per-ore parameters.
  - `OreConfig(enabled, globalMultiplier, veins)` — presets: `vanilla()`, `boosted()` (1.5x iron, 2x gold/diamond), `none()`. 8 default ores: coal, iron, gold, diamond, redstone, lapis, emerald, copper.
  - `OrePopulator(seed, config)` — elongated blob vein placement following vanilla algorithm, applies biome `oreMultiplier`. Only replaces STONE, DEEPSLATE, TERRACOTTA.
- **Terrain Modifiers** (`TerrainModifier.kt`): Post-generation surface modifications.
  - `ModifierConfig` — iceOnFrozenWater, snowOnCold, surfacePatchesEnabled (coarse dirt, gravel, podzol), clayUnderwaterEnabled, iceSpikesEnabled.
  - `TerrainModifier(seed, config, terrain)` — freezes water in frozen biomes, noise-driven surface patches, underwater clay patches, ice spikes in ice_spikes biome (3% per column, 4-25 blocks tall, packed ice tapering).
  - `TerrainModifier.flattenArea(instance, centerX, centerZ, radius, targetY)` — terrain flattening for structures.
- **Population** (`MapPopulator.kt`): Post-generation pass on loaded chunks.
  - `PopulationConfig` — treesEnabled, vegetationEnabled, bouldersEnabled/Chance, pondsEnabled/Chance, mushroomsEnabled, underwaterVegetationEnabled, sugarCaneEnabled, cactusEnabled, lilyPadsEnabled, tallPlantsEnabled, fallenLogsEnabled.
  - Trees: 6 types — oak, birch, spruce, dark oak (2x2 trunk), acacia (bent trunk, flat canopy), jungle (tall with vines). Biome-specific density and tree type selection. Tree spacing enforced via `SPACING_OFFSETS`.
  - Vegetation: biome-specific flowers/grass (flower_plains has 7 flower types, desert has dead bushes, taiga has ferns). Mushrooms in dark_forest/taiga/swamp.
  - Tall plants: double-tall biome-specific plants (sunflower, rose bush, lilac, tall grass, large fern, peony).
  - Fallen logs: 2% per chunk, biome-specific log type, random axis, 3-7 blocks long.
  - Underwater vegetation: kelp columns (2-10 height) in deep water, seagrass/tall_seagrass in shallower water.
  - Sugar cane: near water edges on sand/dirt/grass, 1-3 blocks tall.
  - Cactus: desert only, 1-3 blocks tall, checks no adjacent solid blocks.
  - Lily pads: swamp only, on water surface.
  - Boulders: random stone/granite/andesite/mossy cobblestone spheroids.
  - Ponds: elliptical water bodies with clay bottom and sand edges.
- **Schematic Structures** (`SchematicPopulator.kt`): Terrain-adaptive schematic placement system.
  - `SchematicPopulationConfig(enabled, spacing, chance, definitions, placeholderBlock, replacementBlock)` — controls schematic structure spawning. `placeholderBlock` (default: `minecraft:dragon_egg`) is replaced with `replacementBlock` (default: `minecraft:chest`) during paste, tracked as `chestPositions` in placement results.
  - `SchematicStructureDef` — per-schematic config: id, schematicPath, weight, anchorY, burialDepth, maxSlopeVariance, foundationBlock, rotatable, biomes/excludedBiomes, category (SURFACE/UNDERGROUND/EMBEDDED), minY/maxY, depthFromSurface, generateEntrance, entranceType (SHAFT/STAIRCASE/NONE), shellBlock, lootTableId.
  - `SchematicPlacement` — origin, definitionId, rotation, chestPositions (positions where placeholder blocks were replaced with chests).
  - **Loot integration**: Each `SchematicStructureDef` can specify a `lootTableId`. After schematics are placed, `BattleRoyaleMapGenerator` fills chests via `ChestLootManager.fillChestAt()` using the registered loot table.
  - **SURFACE** placement: samples terrain heights across footprint, rejects steep slopes, places at median height with burial, fills foundation gaps with configurable block, carves terrain intrusions from interior.
  - **UNDERGROUND** placement: carves cavity in rock, optional shell block (stone bricks), pastes schematic inside. Optional entrance (shaft with ladders or spiral staircase).
  - **EMBEDDED** placement: hybrid — partially above/below ground, foundation fill + interior carving.
  - Rotation: 0/90/180/270° support with `facing` and `axis` property rotation for directional blocks.
  - Exclusion zones: chunk-based occupancy grid prevents overlapping.
  - Schematics loaded from `resources/schematics/` via Sponge `.schem` format.
- **Integration** (`BattleRoyaleMapGenerator.kt`): Full pipeline — `BiomeRegistry.registerDefaults()` + custom biomes → `TerrainGenerator` → chunk preload → `CaveCarver.carveAll()` → `OrePopulator.populateAll()` → `TerrainModifier.applyAll()` → `MapPopulator.populate()` → `CaveCarver.decorateAll()` → `SchematicPopulator.populate()` → loot chest filling. Returns `GeneratedMap(instance, center, mapRadius, schematicPlacements)`.
- **MapPresets** (`MapPresets.kt`): Programmatic map generation configs. `MapPresets[name]` returns a `MapGenerationConfig`. Avoids Gson serialization issues with `Block` fields and Kotlin default values (`Unsafe.allocateInstance` doesn't run init blocks). `"perfect"` / `"battleroyale"` preset: seed=currentTimeMillis, mapRadius=250, 16 spawn ring at r=160, dense terrain (scale 0.008, continental influence 18, erosion 0.35), rivers, overhangs, 12-radius biome blending, dense caves, boosted ores (1.3x global), all modifiers, all population, all `BiomePresets.all()` custom biomes.
- **Mode override**: When `config.mapGeneration` or `config.mapPreset` is non-null, `BattleRoyaleMode` overrides `createGameInstance()` (uses generated instance) and `spawnPoint` (uses generated center). `SpawnModeExecutor` uses the generated map radius for spawn placement. Resolution order: `mapGeneration` → `MapPresets[mapPreset]` → `null` (anvil world). `_generatedMap` is mutable — reset to null in `onGameReset()` together with `invalidateGameInstance()` for round-based regeneration in dual-instance mode.
- **Dual-instance**: `config.lobbyWorld` (optional `LobbyWorldConfig`) passed through to `GameSettings.lobbyWorld`. When set, players wait in lobby world and transfer to game world on PLAYING. Default config includes `lobbyWorld` with `worlds/lobby` path.

## Module System — `module/OrbitModule.kt`
Extends Ether's `Module(name, canReload = true)`. Scoped `EventNode<Event>` attaches/detaches in one operation. Override `commands()` to provide commands. Managed via `app.modules`.

### Lifecycle Hooks
- **`onPlayerDisconnect(callback: (Player) -> Unit)`** — registers a lambda called on `PlayerDisconnectEvent`. Only adds the listener if any callbacks are registered.
- **`ConcurrentHashMap<K, *>.cleanOnInstanceRemove(extractHash: (K) -> Int)`** — registers a map for periodic stale-instance eviction. A 30-second sweep task compares map keys against live instances and removes entries whose instance no longer exists.
- **`MutableSet<K>.cleanOnInstanceRemove(extractHash: (K) -> Int)`** — same for `ConcurrentHashMap.newKeySet()` sets.
- Sweep task only created if maps/sets registered. Disconnect listener only registered if callbacks exist. Both scoped to module lifecycle.

## Mechanic System — `mechanic/` (263 modules)
Modular vanilla Minecraft mechanics. Each is an `OrbitModule` subclass using scoped `EventNode` and `Tag<T>` for state.

### Configuration
- `MECHANICS` env var: csv of module names. If unset + `GAME_MODE` set: all 263 enabled. If unset + no `GAME_MODE`: none.
- All mechanics: `combat`, `fall-damage`, `block`, `food`, `item-drop`, `death`, `experience`, `armor`, `projectile`, `void-damage`, `fire`, `drowning`, `sprint`, `weather`, `day-night`, `bed`, `explosion`, `natural-spawn`, `container`, `crafting`, `potion`, `crop`, `fishing`, `anvil`, `breeding`, `beacon`, `enchanting`, `respawn-anchor`, `trading`, `door`, `shield`, `enderpearl`, `snowball`, `elytra`, `trident`, `crossbow`, `lever`, `noteblock`, `composter`, `sign`, `furnace-smelting`, `armor-stand`, `painting`, `lectern`, `jukebox`, `cauldron`, `item-frame`, `campfire`, `grindstone`, `stonecutter`, `loom`, `cartography-table`, `smithing-table`, `ender-chest`, `bell`, `candle`, `shulker-box`, `scaffolding`, `boat`, `ladder`, `dispenser`, `hopper`, `conduit`, `copper-oxidation`, `turtle-egg`, `beehive`, `lightning-rod`, `gravity`, `bonemeal`, `piston`, `redstone-wire`, `minecart`, `armor-trim`, `firework`, `snow-layer`, `ice`, `cactus`, `vine-growth`, `bamboo`, `sugarcane`, `mushroom`, `coral`, `dragon-breath`, `wither`, `water-flow`, `lava-flow`, `bubble-column`, `item-durability`, `double-chest`, `cobweb`, `hoe-tilling`, `axe-stripping`, `shovel-pathing`, `protection-enchant`, `fire-aspect`, `thorns`, `mending`, `silk-touch`, `sweeping`, `sharpness`, `knockback-enchant`, `power`, `flame`, `infinity`, `fortune`, `efficiency`, `unbreaking`, `looting`, `loyalty`, `channeling`, `quick-charge`, `wind-charge`, `mace`, `trial-spawner`, `sculk-catalyst`, `copper-bulb`, `crafter`, `vault`, `ominous-bottle`, `item-frame-rotation`, `suspicious-stew`, `sculk-vein`, `breeze`, `spore-blossom`, `sculk-sensor-calibrated`, `brushable`, `frog`, `allay`, `warden`, `smelting-drop`, `nether-wart`, `sniffer`, `camel`, `hanging-block`, `mud`, `mangrove`, `cherry`, `pitcher-plant`, `torchflower`, `wax`, `azalea`, `bamboo-mosaic`, `recovery-compass`, `echo-shard`, `slime-block-bounce`, `noteblock-instrument`, `dragon-egg`, `respawn-module`, `head-drop`, `compass-tracking`, `spyglass`, `goat-horn`, `bundle`, `amethyst-growth`, `light-block`, `structure-void`, `barrier-block`, `command-block`, `skull`, `netherite-damage`, `sweet-berry-bush`, `powder-snow-freezing`, `drowned-conversion`, `piglin-barter`, `enderman-pickup`, `creeper-explosion`, `skeleton-shoot`, `zombie-attack`, `wither-skeleton`, `blaze-behavior`, `ghast-behavior`, `slime-split`, `phantom`, `vex`, `spider-climb`, `guardian-beam`, `shulker-bullet`, `witch-potion`, `evoker-fangs`, `ravager`, `pillager-crossbow`, `snow-golem`, `fox-sleep`, `parrot-dance`, `dolphin-grace`, `bee-pollination`, `silverfish-burrow`, `cat-creeper`, `turtle-scute`, `villager-profession`, `wandering-trader`, `raid-system`, `elder-guardian`, `zombie-siege`, `piglin-aggro`, `hoglin-behavior`, `strider-behavior`, `wolf-taming`, `cat-taming`, `horse-taming`, `llama-behavior`, `panda-behavior`, `polar-bear`, `axolotl-behavior`

### Core Mechanics (19)
| Module | Summary |
|---|---|
| `combat` | Melee damage, attack cooldown, crits (1.5x), knockback, 500ms iframes |
| `fall-damage` | Fall height tracking, landing damage `(startY - landY - 3)` |
| `food` | Hunger, eating via DataComponents, exhaustion, regen, starvation. Exposes `Player.addExhaustion()` |
| `item-drop` | Drop/pickup items, 2s pickup delay, 5min despawn |
| `block` | Break drops via BlockDropData, tool requirement, placement validation |
| `death` | Death inventory scatter, respawn reset (heal, food=20, saturation=5) |
| `experience` | XP orbs, vanilla level formula, `Player.giveExperience()` |
| `armor` | Vanilla damage reduction via Attribute.ARMOR/ARMOR_TOUGHNESS |
| `projectile` | Bow charging, arrow spawning with velocity, damage scaling |
| `void-damage` | Kill below Y=-64 |
| `fire` | Fire/lava contact damage via `entityMeta.setOnFire(true)` |
| `drowning` | Air supply (300), drowning damage at 0 |
| `sprint` | Sprint exhaustion, auto-stop at food<=6 |
| `weather` | Per-instance weather cycling (clear→rain→thunder) |
| `day-night` | `instance.timeRate = 1` for vanilla day/night |
| `bed` | Respawn point, sleep skip at >=50% sleeping |
| `explosion` | TNT ignition, spherical blast, entity damage+knockback |
| `natural-spawn` | Mob spawning with AI goals, max 100, 5min despawn |
| `container` | Block-tied inventories (chest, barrel, hopper, furnace, etc.) |

### Advanced Mechanics (10)
| Module | Summary |
|---|---|
| `crafting` | Opens crafting inventory on crafting table interaction |
| `potion` | Reads `DataComponents.POTION_CONTENTS`, applies effects, replaces with glass bottle |
| `crop` | Tick-based crop growth (wheat, carrots, potatoes, beetroots, stems, nether_wart) |
| `fishing` | Fishing bobber casting, bite timer, weighted fish loot table |
| `anvil` | Opens anvil inventory on anvil block interaction |
| `breeding` | Animal breeding with per-type food maps, love mode, breed cooldowns |
| `beacon` | Pyramid tier scanning (1-4) via `BlockPositionIndex`, tier cache with invalidation, tier-based potion effects |
| `enchanting` | Opens enchantment inventory on enchanting table |
| `respawn-anchor` | Glowstone charging (up to 4), set respawn point |
| `trading` | Opens merchant inventory on villager/wandering_trader interaction |

### Interaction & Projectile Mechanics (12)
| Module | Summary |
|---|---|
| `door` | Door/trapdoor/fence gate open/close, double door sync, sound effects |
| `shield` | Shield blocking damage, 200ms warmup, attacker knockback |
| `enderpearl` | Ender pearl teleportation, 5 fall damage on land |
| `snowball` | Snowball/egg throwable projectiles, knockback, egg chicken spawn |
| `elytra` | Elytra gliding, firework rocket boost |
| `trident` | Trident throwing, 8 damage, knockback |
| `crossbow` | Crossbow loading (1.25s), firing with 9 damage arrows |
| `lever` | Lever toggle, button auto-reset (20/30 ticks), pressure plate |
| `noteblock` | Note block tuning (25 pitches), instrument sounds |
| `composter` | Composter interaction, compostable items (30-100% chance), bone meal output |
| `sign` | Sign placement tracking, text storage |
| `furnace-smelting` | Furnace/smoker/blast furnace smelting with 27 recipes, fuel system |

### Entity & Decoration Mechanics (5)
| Module | Summary |
|---|---|
| `armor-stand` | Armor stand placement, equipment slots, interaction/break |
| `painting` | Painting placement on wall faces, break to drop |
| `item-frame` | Item frame placement, 8-state rotation, item insert/remove, glow support |
| `boat` | 18 boat types (wood + chest variants), water placement, mount/dismount, break to drop |
| `gravity` | Gravity blocks (sand, gravel, anvil, dragon egg, concrete powder), falling entities, landing |

### Workstation UI Mechanics (8)
| Module | Summary |
|---|---|
| `grindstone` | Opens grindstone inventory on block interaction |
| `stonecutter` | Opens stonecutter inventory on block interaction |
| `loom` | Opens loom inventory on block interaction |
| `cartography-table` | Opens cartography table inventory on block interaction |
| `smithing-table` | Opens smithing table inventory on block interaction |
| `ender-chest` | Per-player ender chest with persistent inventory, open sound |
| `lectern` | Lectern book placement and reading |
| `jukebox` | Jukebox disc insert/eject |

### Block Interaction Mechanics (9)
| Module | Summary |
|---|---|
| `bell` | Bell ring sound on interaction |
| `candle` | Candle light/extinguish (flint & steel), stacking (1-4), all 17 colors |
| `cauldron` | Cauldron water level fill/drain, bucket and bottle interaction |
| `shulker-box` | 17 colored shulker boxes, persistent per-block inventory, open sound |
| `scaffolding` | Scaffolding placement with max distance (6), stability check |
| `ladder` | Ladder/vine climbing, slow descent when sneaking |
| `copper-oxidation` | Oxidation stages, waxing with honeycomb, scraping with axe |
| `turtle-egg` | Egg trampling, hatching over time, stacking (1-4) |
| `bonemeal` | Bone meal crop growth, sapling handling, grass flower spreading |

### Redstone & Environment Mechanics (5)
| Module | Summary |
|---|---|
| `dispenser` | Dispenser/dropper item ejection with directional velocity |
| `hopper` | Hopper persistent inventory, open on interaction |
| `conduit` | Conduit power effect, prismarine frame detection (16+ blocks), 32-block range |
| `beehive` | Honey level tracking, glass bottle → honey bottle, shears → honeycomb |
| `lightning-rod` | Lightning rod tracking, nearest rod finder, powered state management |
| `campfire` | Campfire cooking (8 recipes, 30s), contact damage (1/2 for soul), lit/unlit state |

### Plant Growth Mechanics (5)
| Module | Summary |
|---|---|
| `cactus` | Cactus contact damage, growth (max 3), placement validation (sand only, no adjacent) |
| `sugarcane` | Sugar cane tick-based growth (max 3) |
| `bamboo` | Bamboo growth (max 16) |
| `vine-growth` | Vine downward spreading over time |
| `mushroom` | Mushroom spreading in dark areas |

### Redstone & Technical Mechanics (4)
| Module | Summary |
|---|---|
| `piston` | Piston extend/retract, block pushing (max 12), sticky pull, immovable block list |
| `redstone-wire` | Redstone wire connection updates (side/up/none), neighbor propagation |
| `minecart` | 5 minecart types, rail placement, mount/dismount, break to drop |
| `firework` | Firework rocket launching (use item + block interact), timed despawn |

### World & Environment Mechanics (6)
| Module | Summary |
|---|---|
| `snow-layer` | Snow layer stacking (1-8), shovel clearing, snow block removal |
| `ice` | Ice breaking → water conversion |
| `coral` | Coral dying outside water (15 types), dead coral block replacement |
| `gravity` | Gravity blocks (sand, gravel, anvil, dragon egg, concrete powder), falling entities |
| `dragon-breath` | Dragon breath collection with glass bottles |
| `wither` | Wither skull pattern detection, T-shape soul sand validation, structure clearing |

### Armor, Items & Survival Mechanics (5)
| Module | Summary |
|---|---|
| `armor-trim` | Smithing table armor trim UI |
| `cake` | Cake eating per slice (7 bites), food+saturation restore |
| `totem` | Totem of undying death prevention, regen/absorption/fire resistance effects |
| `flower-pot` | Flower pot planting (31 plant types), empty pot on interact |
| `item-repair` | Anvil repair UI |

### Portal & Structure Mechanics (2)
| Module | Summary |
|---|---|
| `nether-portal` | Obsidian frame detection, flint & steel activation, portal block filling |
| `wither` | Wither skull T-shape pattern detection, soul sand validation |

### Connection & Enchantment Mechanics (3)
| Module | Summary |
|---|---|
| `fence` | Fence/wall connection updates (11 wood types, 22 wall types), neighbor propagation |
| `frost-walker` | Frost walker ice creation on water, 10s melt timer |
| `map` | Map creation via cartography table |

### Wave 7 — Portals, Constructs & Environment (15)
| Module | Summary |
|---|---|
| `end-portal` | End portal frame eye insertion, 12-frame detection, portal filling |
| `brewing-stand` | Brewing stand inventory, ingredient/fuel tracking, 400-tick brew timer |
| `banner` | Banner placement/break, 16 color drop mapping |
| `spawner` | Mob spawner block via `BlockPositionIndex`, per-type entity spawning, 4 max entities, 400-tick cycle |
| `chorus` | Chorus fruit teleport (16 attempts), chorus plant cascading break |
| `honey-block` | Honey block slowdown, slime block bounce |
| `magma-block` | Magma block contact damage (1f), sneaking bypass |
| `sponge` | Sponge water absorption (65 max, range 7), wet sponge conversion |
| `observer` | Observer block state change detection, 2-tick powered pulse |
| `repeater` | Repeater delay cycling (1-4), comparator mode toggle (compare/subtract) |
| `soul-speed` | Soul sand/soil velocity boost (1.3x) |
| `depth-strider` | Underwater movement boost (1.2x) |
| `kelp` | Kelp underwater growth, age progression (0-25) |
| `end-crystal` | End crystal placement on obsidian/bedrock, explosion on attack (6 radius, 12 damage) |
| `iron-golem` | Iron golem T-shape construction (iron blocks + pumpkin), snow golem detection |

### Wave 8 — Block Placement & Workstations (15)
| Module | Summary |
|---|---|
| `slab` | Slab double-placement (bottom+top = double), 50+ slab types |
| `stairs` | Stair shape detection (inner/outer corners), 45+ stair types |
| `trapdoor` | Trapdoor open/close toggle, 11 wood types, iron excluded |
| `glass-pane` | Glass pane/iron bars connection updates (NSEW), 18 pane types |
| `rail` | Rail shape auto-detection (curved/straight), 4 rail types |
| `torch` | Torch placement validation, support block breaking cascade |
| `end-rod` | End rod facing alignment with adjacent rods |
| `chain` | Chain axis alignment with adjacent chains |
| `barrel` | Barrel persistent inventory, open sound/state |
| `smoker` | Smoker inventory on interaction |
| `blast-furnace` | Blast furnace inventory on interaction |
| `fletching-table` | Fletching table crafting UI |
| `lantern` | Lantern placement validation (hanging/standing), support break cascade |
| `chorus-flower-growth` | Chorus flower vertical growth, age progression, plant stem conversion |
| `wither-rose` | Wither rose contact wither effect |

### Wave 9 — Modern Blocks & Archaeology (13)
| Module | Summary |
|---|---|
| `dropper` | Dropper block inventory |
| `lily-pad` | Lily pad water-only placement, drop on break |
| `sea-lantern` | Sea lantern prismarine crystal drops (2-3) |
| `glow-lichen` | Glow lichen shear harvesting |
| `moss` | Moss bone meal spreading, stone/dirt conversion in radius |
| `sculk-shrieker` | Sculk shrieker activation on non-sneaking movement, shriek sound, 10s cooldown |
| `decorated-pot` | Decorated pot single-item storage, break drops contents |
| `chiseled-bookshelf` | Chiseled bookshelf 6-slot book storage, slot property updates |
| `hanging-sign` | Hanging sign placement validation (requires above block) |
| `suspicious-sand` | Archaeology brushing (4 stages), loot table on complete |
| `pointed-dripstone-water` | Dripstone water dripping via `BlockPositionIndex` spatial lookup, cauldron filling |
| `respiration` | Underwater breath tracking (300 ticks), recovery on surface |
| `tinted-glass` | Tinted glass drops self on break |

### Wave 10 — Fluids, Durability & Tool Interactions (8)
| Module | Summary |
|---|---|
| `water-flow` | Event-driven water spreading cascade (5-tick delay per step, max depth 7), horizontal + downward flow, gap-filling on break |
| `lava-flow` | Event-driven lava spreading cascade (30-tick delay per step, max depth 3), lava-water interaction (cobblestone/obsidian) |
| `bubble-column` | Bubble column push (soul sand up) / pull (magma block down) on player movement |
| `item-durability` | Weapon/tool/armor durability via DataComponents.DAMAGE, break at max |
| `double-chest` | Adjacent chest detection, 6-row double chest inventory, ordered key deduplication |
| `cobweb` | Cobweb velocity reduction (0.05x) on player movement |
| `hoe-tilling` | Hoe right-click converts dirt/grass/path/coarse/rooted to farmland with sound |
| `axe-stripping` | Axe right-click strips logs/wood/stems/hyphae (10 wood types, 20 mappings) with sound |

### Wave 11 — Enchantment Mechanics & Tool Actions (7)
| Module | Summary |
|---|---|
| `shovel-pathing` | Shovel right-click converts grass/dirt/coarse/mycelium/podzol/rooted to dirt path with sound |
| `protection-enchant` | Protection enchantment damage reduction (4%/level, max 20), specialized fire/blast/projectile/feather falling |
| `fire-aspect` | Fire Aspect enchantment sets target on fire for level*4s on melee attack |
| `thorns` | Thorns enchantment reflects 1-4 damage with level*15% chance on melee hit |
| `mending` | Mending enchantment repairs damaged equipment when picking up nearby XP orbs (2 durability/XP) |
| `silk-touch` | Silk Touch drops block itself (ores, glass, glowstone, ice, bookshelves, grass, mycelium, sculk) |
| `sweeping` | Sweep attack hits nearby entities (1.0 block radius), sweep edge enchantment scaling, sweep particle via `spawnParticleAt` util |

### Wave 12 — Enchantments, 1.21 Features & Combat (15)
| Module | Summary |
|---|---|
| `sharpness` | Sharpness (+0.5*level+0.5), Smite (vs undead, +2.5*level), Bane of Arthropods (vs arthropods, +2.5*level) extra melee damage |
| `knockback-enchant` | Knockback enchantment adds extra velocity (0.5*level multiplier) on melee attack |
| `power` | Power enchantment increases arrow damage by 0.5*(level+1) via Tag on arrow entity |
| `flame` | Flame enchantment sets arrow targets on fire for 5s, tracked via Tag on arrow entity |
| `infinity` | Infinity enchantment restores consumed arrow after bow shot |
| `fortune` | Fortune enchantment multiplies ore drops (bonus 0..level), 18 ore-to-drop mappings |
| `efficiency` | Efficiency enchantment tracking via Tag for tool mining speed bonus |
| `unbreaking` | Unbreaking enchantment 1/(level+1) chance to actually take durability damage on attack/break/hit |
| `looting` | Looting enchantment spawns bonus mob drops (0..level) on kill, mob-type-aware drop materials |
| `loyalty` | Loyalty enchantment returns thrown trident to owner with velocity tracking, level-scaled return speed |
| `channeling` | Channeling enchantment strikes lightning (5 damage, fire) on trident melee hit during thunderstorm |
| `quick-charge` | Quick Charge enchantment reduces crossbow loading time by 0.25s per level |
| `wind-charge` | 1.21 wind charge throwable, sphere knockback (radius 2.5), no damage, projectile collision detection |
| `mace` | 1.21 mace weapon, fall-distance-scaled damage (base 6 + (fallDist-1.5)*4), bounce on smash hit |
| `trial-spawner` | 1.21 trial spawner block, proximity activation (8 blocks), 3-6 hostile mobs, reward drop on completion, 30min cooldown |

### Wave 13 — Deep Dark, 1.21 Blocks & Mob Behaviors (15)
| Module | Summary |
|---|---|
| `sculk-catalyst` | Sculk catalyst spreads sculk blocks in 3-block radius around mob deaths within 8 blocks, timer-based death scanning |
| `copper-bulb` | 1.21 copper bulb light toggle on interaction, all 8 oxidation/waxed variants, lit property toggling |
| `crafter` | 1.21 crafter block opens 3x3 crafting grid on interaction, persistent per-block inventory |
| `vault` | 1.21 vault block gives random reward on first interaction per player, ConcurrentHashMap claim tracking |
| `ominous-bottle` | Ominous bottle applies Bad Omen effect (6000 ticks) on use, consumes item, drink sound |
| `item-frame-rotation` | Enhanced item frame rotation cycling through 8 states on punch, drops item at max rotation |
| `suspicious-stew` | Suspicious stew applies random potion effect (5-15s) when eaten, replaces with bowl |
| `sculk-vein` | Sculk vein placement validation (requires adjacent solid block), multi-face property tracking |
| `breeze` | Breeze mob wind charge attack behavior, 3s cooldown, sphere knockback on projectile impact |
| `spore-blossom` | Spore blossom dripping particle effect via `BlockPositionIndex` spatial lookup, FALLING_SPORE_BLOSSOM particles |
| `sculk-sensor-calibrated` | Calibrated sculk sensor frequency setting (1-15) via interaction, vibration matching via `BlockPositionIndex` spatial lookup |
| `brushable` | Extended archaeology for suspicious gravel, 4-stage brush progress, random archaeological loot |
| `frog` | Frog eats small slimes/magma cubes, produces variant-based froglight (warm=pearlescent, temperate=ochre, cold=verdant) |
| `allay` | Allay item collection within 32 blocks via `EntitySpawnEvent` tracking, delivers to nearest note block, material matching via Tag |
| `warden` | Warden spawns after 3 sculk shrieker activations, sonic boom ranged attack (10 dmg, 5s cooldown), darkness effect |

### Wave 14 — Modern Plants, Mobs & Crafting (15)
| Module | Summary |
|---|---|
| `smelting-drop` | Auto-smelt mining drops when tool has auto_smelt Tag, 12 ore-to-ingot/material mappings |
| `nether-wart` | Nether wart growth on soul sand, tick-based age 0-3 progression, placement validation |
| `sniffer` | Sniffer mob digging behavior, produces torchflower_seeds or pitcher_pod, 2min cooldown via Tag |
| `camel` | Camel mount with dash ability on sprint, velocity burst in look direction, 5s cooldown, 2-rider support |
| `hanging-block` | Hanging block placement validation (propagule, spore blossom, etc.), cascading break on support removal |
| `mud` | Water bottle on dirt converts to mud, mud above pointed dripstone dries to clay (timer-based), ConcurrentHashMap tracking |
| `mangrove` | Mangrove propagule growth, tick-based age 0-4 progression for hanging propagules |
| `cherry` | Cherry blossom petal particles via `BlockPositionIndex` spatial lookup near cherry_leaves blocks, CHERRY_LEAVES particle with POOF fallback |
| `pitcher-plant` | Pitcher crop growth age 0-4, becomes 2-block tall pitcher plant at max age |
| `torchflower` | Torchflower crop growth age 0-2, becomes torchflower block at max age |
| `wax` | Honeycomb waxing on copper blocks, 36 copper variant mappings (blocks, doors, trapdoors, grates, bulbs, chiseled) |
| `azalea` | Azalea bush bone meal growth into azalea tree (4-block oak log trunk + azalea/flowering leaves sphere) |
| `bamboo-mosaic` | Bamboo block stripping with axe, bamboo_block to stripped_bamboo_block conversion |
| `recovery-compass` | Recovery compass tracks last death location via Tags, action bar distance display |
| `echo-shard` | Echo shard crafting shortcut: 8 echo shards + compass on crafting table produces recovery compass |

### Wave 15 — Special Blocks, Items & Display (15)
| Module | Summary |
|---|---|
| `slime-block-bounce` | Player lands on slime block → bounce (negate + 0.8x Y velocity), cancel fall damage |
| `noteblock-instrument` | Note block instrument determined by block below (16 instrument-to-block mappings) |
| `dragon-egg` | Dragon egg teleports to random nearby position (radius 15) when clicked |
| `respawn-module` | Full respawn handling: heal, food=20, saturation=5, clear effects, respawn position |
| `head-drop` | Player kill drops victim skull as PLAYER_HEAD item with victim name in lore |
| `compass-tracking` | Lodestone compass position binding on lodestone interaction, position storage via Tag |
| `spyglass` | Spyglass zoom with slowness effect on use, cancel on stop |
| `goat-horn` | Goat horn 8 variant sounds on use, 7s cooldown via `Cooldown<UUID>` util |
| `bundle` | Bundle item GUI storage (1-row inventory), 64-item max capacity, per-player tracking |
| `amethyst-growth` | Amethyst bud growth stages on budding amethyst (small→medium→large→cluster), random face |
| `light-block` | Light block adjustable level (0-15) cycling on interaction |
| `structure-void` | Structure void blocks: invisible, no collision for non-creative, instant break |
| `barrier-block` | Barrier blocks: invisible to non-creative, full collision, particle display via `BlockPositionIndex` spatial lookup |
| `command-block` | Command block stored command framework, GUI for ops, redstone execution |
| `skull` | Player/mob skull placement with owner tracking, texture display, owner-aware drops |

### Wave 16 — Mob Behaviors & Environmental Hazards (15)
| Module | Summary |
|---|---|
| `netherite-damage` | Netherite armor knockback resistance: 10% per piece (max 40%), velocity reduction on entity damage |
| `sweet-berry-bush` | Extended sweet berry bush: 0.2x velocity slowdown, 1 damage/sec timer while inside bush |
| `powder-snow-freezing` | Powder snow freeze timer: 1 damage/sec after 7s in powder snow, leather boots bypass |
| `drowned-conversion` | Zombie underwater 30s converts to drowned, timer-based scanning, AI group setup |
| `piglin-barter` | Piglin bartering: gold ingot pickup, 6s wait, weighted loot table (21 items) drop |
| `enderman-pickup` | Enderman random block pickup (15 types) and placement, held block via Tag |
| `creeper-explosion` | Creeper hiss + 1.5s fuse + spherical explosion (radius 3), charged creepers double radius |
| `skeleton-shoot` | Skeleton arrow shooting at nearest player (16 blocks), 2s cooldown, 2-5 random damage |
| `zombie-attack` | Zombie melee AI: path to nearest player (16 blocks), 3 damage on contact, AI goals |
| `wither-skeleton` | Wither skeleton: 5 melee damage + Wither I (10s), 2.5% wither skull drop on death |
| `blaze-behavior` | Blaze 3-fireball burst at nearest player (16 blocks), 3s cooldown, fire at impact |
| `ghast-behavior` | Ghast large fireball, deflectable by player attack, explosion + fire on impact |
| `slime-split` | Slime/magma cube splitting: large→2 medium→2 small on death, size via Tag |
| `phantom` | Phantom spawning after 72000 insomnia ticks, night-only, swooping attack pattern |
| `vex` | Vex mob: no gravity, 9 damage, target tracking, 30s lifetime auto-remove |

### Wave 17 — Mob Behaviors & Environmental Interactions (15)
| Module | Summary |
|---|---|
| `spider-climb` | Spider wall climbing: spiders climb vertical surfaces, 0.2 upward velocity when adjacent to solid wall blocks |
| `guardian-beam` | Guardian laser beam: target nearest player (16 blocks), 6 damage after 2s charge, Tag-based beam tracking |
| `shulker-bullet` | Shulker homing bullet: projectile tracks nearest player (16 blocks), Levitation I (10s) on hit, 3s cooldown |
| `witch-potion` | Witch splash potions: random Poison/Slowness/Weakness/Harming at nearest player (16 blocks), 3s cooldown |
| `evoker-fangs` | Evoker fang line: 16 sequential damage zones toward target, 6 damage per fang, 5s cooldown |
| `ravager` | Ravager charge: 12 melee damage, breaks leaf blocks on contact, 4s cooldown, 12-block range |
| `pillager-crossbow` | Pillager crossbow: arrow projectile at nearest player (16 blocks), 3-6 damage, 2s cooldown |
| `snow-golem` | Snow golem: leaves snow trail, throws snowballs at hostile mobs (10 blocks), 1s cooldown |
| `fox-sleep` | Fox day sleeping: sits during daytime (0-12000), wakes at night, FoxMeta sitting pose |
| `parrot-dance` | Parrot jukebox dance: parrots within 3 blocks of active jukebox dance, stop when jukebox stops |
| `dolphin-grace` | Dolphin's Grace: players within 5 blocks of dolphin in water receive Dolphin's Grace potion effect |
| `bee-pollination` | Bee pollination: bees collect nectar from flowers, return to hive, increment honey_level (Tag-based) |
| `silverfish-burrow` | Silverfish burrowing: idle silverfish convert stone/cobblestone/stone_bricks to infested variants, spawn on infested block break |
| `cat-creeper` | Cat scares creepers: creepers flee from cats/ocelots within 6 blocks, velocity away from nearest cat |
| `turtle-scute` | Baby turtle scute drop: baby turtles drop scute item on growth (24000 tick age transition) |

### Wave 18 — Village, Nether & Animal Taming (15)
| Module | Summary |
|---|---|
| `villager-profession` | Villager workstation assignment: 40-tick scan, block-to-profession mapping (13 workstations), profession via Tag |
| `wandering-trader` | Wandering trader spawning near players every 24000 ticks, despawn after 48000 ticks, 2 trader llama companions |
| `raid-system` | Raid waves on Bad Omen + bell proximity, pillager/vindicator spawn escalation (5 waves), per-instance ConcurrentHashMap tracking |
| `elder-guardian` | Elder guardian Mining Fatigue III (6000 ticks) within 50 blocks, 60s refresh scan |
| `zombie-siege` | Zombie siege at villages: night-only (18000-23000), bell + 20 beds required, max 20 zombies per siege |
| `piglin-aggro` | Piglin gold armor check: attack players not wearing gold within 10 blocks, AI goal configuration |
| `hoglin-behavior` | Hoglin melee attack (6 damage, 8 blocks, 2s cooldown), flee from warped fungus within 7 blocks |
| `strider-behavior` | Strider lava walking, passenger steering with warped fungus on stick, shiver speed reduction outside lava |
| `wolf-taming` | Wolf taming with bone (33% chance), sit on shift-click, follow owner, attack owner's attacker |
| `cat-taming` | Cat taming with raw cod/salmon (33% chance), sit on shift-click, follow owner |
| `horse-taming` | Horse taming via repeated mounting, increasing tame chance per attempt, saddle for steering control |
| `llama-behavior` | Llama spit at hostile mobs (10 blocks, 1 damage, 3s cooldown), caravan formation when leashed |
| `panda-behavior` | Panda gene system via Tag: lazy roll, playful somersault, aggressive retaliation attack (6 damage) |
| `polar-bear` | Polar bear neutral behavior: attacks when baby nearby is hit, 6 damage, 12-block aggro range |
| `axolotl-behavior` | Axolotl attacks drowned/guardians via `EntitySpawnEvent` tracking + `nearbyEntities`, plays dead at low HP (regenerates), grants Regeneration I to nearby players |

## Custom Content System — `utils/customcontent/` (23 files)
Custom item, block, and 3D armor system with JSON config or code DSL definitions, Blockbench `.bbmodel` model source, GLSL shader generation, and resource pack merging.

### Architecture
| File | Summary |
|---|---|
| `CustomContentRegistry.kt` | Central singleton: init, load JSON, allocate states, register items/blocks, merge pack. Top-level `customItem {}` and `customBlock {}` DSL functions |
| `item/CustomItem.kt` | Item data class (`id`, `baseMaterial`, `customModelDataId`, `displayName`, `lore`, `unbreakable`, `glowing`, `maxStackSize`, `modelPath`), `createStack(amount)` |
| `item/CustomItemRegistry.kt` | ConcurrentHashMap registry: `get(id)`, `require(id)`, `byCustomModelData(cmd)`, `all()` |
| `item/CustomItemLoader.kt` | JSON deserialization → `CustomItemDefinition`, `CustomItemDsl` builder class |
| `block/BlockHitbox.kt` | Sealed class: `Full` (592), `Slab` (53), `Stair` (50), `Thin` (17), `Transparent` (128), `Wall` (22), `Fence` (12), `Trapdoor` (11). `fromString()` companion |
| `block/BlockStateAllocator.kt` | Deterministic vanilla block state pools per hitbox type, `allocate(id, hitbox)`, `isAllocated(block)`, `fromVanillaBlock(block)`. Persists to `data/customcontent/allocations.dat` |
| `block/CustomBlock.kt` | Block data class (`id`, `hitbox`, `itemId`, `customModelDataId`, `hardness`, `drops`, `modelPath`, `placeSound`, `breakSound`, `allocatedState`). `CustomBlockDrops` sealed: `SelfDrop`, `LootTableDrop` |
| `block/CustomBlockRegistry.kt` | ConcurrentHashMap registry: `get(id)`, `require(id)`, `fromVanillaBlock(block)`, `fromItemId(itemId)`, `all()` |
| `block/CustomBlockLoader.kt` | JSON deserialization → `CustomBlockDefinition`, `CustomBlockDsl` and `CustomBlockDropsDsl` builder classes |
| `event/CustomBlockPlaceHandler.kt` | `PlayerBlockPlaceEvent` listener: detects held CustomModelData item → sets allocated vanilla block state, decrements item, plays place sound |
| `event/CustomBlockBreakHandler.kt` | `PlayerBlockBreakEvent` listener: detects custom block → spawns drops (self or loot table), plays break sound |
| `event/CustomBlockInteractHandler.kt` | `PlayerBlockInteractEvent` listener: cancels vanilla interactions on custom block states |
| `pack/PackMerger.kt` | Merges ModelEngine bones + custom item/block models + armor shader entries into single ZIP with SHA-1. `merge(modelsDir, rawResults, armorShaderEntries)` → `MergeResult(packBytes, sha1)`. All ModelEngine assets under `modelengine` namespace. Armor shader entries injected as-is (paths like `assets/minecraft/shaders/...`) |
| `pack/ItemModelOverrideWriter.kt` | Generates `models/item/{material}.json` with sorted CustomModelData override entries |
| `pack/BlockStateWriter.kt` | Generates `blockstates/{block}.json` with complete `variants` for `full` (note_block/mushroom) and `thin` (carpet) hitbox types. Non-allocated states fall back to vanilla model. Other hitbox types skip blockstate generation (placed block renders as vanilla material) |

### Block State Pools
| Hitbox | Source | Count | Custom Visual |
|---|---|---|---|
| `full` | note_block (16 instruments × 25 notes × powered=false) + brown/red mushroom_block + mushroom_stem (64 face combos each) | 592 | Yes — complete `variants` blockstate with vanilla fallback |
| `thin` | 17 carpet colors | 17 | Yes — single-variant blockstate replacement |
| `slab` | 53 slab materials, canonical `type=bottom,waterlogged=false` | 53 | No — vanilla material visual when placed |
| `stair` | 50 stair materials, canonical `facing=north,half=bottom,shape=straight,waterlogged=false` | 50 | No — vanilla material visual when placed |
| `transparent` | tripwire 7-boolean combos | 128 | No — vanilla material visual when placed |
| `wall` | 22 wall materials, canonical isolated state | 22 | No — vanilla material visual when placed |
| `fence` | 12 fence materials, canonical disconnected state | 12 | No — vanilla material visual when placed |
| `trapdoor` | 11 trapdoor materials (no iron), canonical closed state | 11 | No — vanilla material visual when placed |

Non-`full`/`thin` hitbox types provide the correct collision shape but render as their vanilla material when placed. This is because replacing blockstate files for complex blocks (slabs, stairs, fences, walls, trapdoors, tripwire) would break all vanilla instances of that material. The custom model is only visible on the held item.

### Mechanic Guards
- `NoteBlockModule` — skips tuning for `BlockStateAllocator.isAllocated(block)`
- `BlockModule` — skips vanilla drops for allocated states
- `SlabModule` — skips double-slab stacking for allocated states

### Data Directories
```
data/customcontent/
├── items/           (JSON item definitions)
├── blocks/          (JSON block definitions)
├── models/          (.bbmodel files for items/blocks)
├── armors/          (.bbmodel files for 3D armor sets)
├── allocations.dat  (persistent block state allocations)
├── model_ids.dat    (persistent CustomModelData IDs)
└── pack.zip         (merged resource pack output)
```

### Custom 3D Armor — `armor/` (8 files)
Shader-based 3D armor rendering. Players equip dyed leather armor; client-side GLSL raycasting replaces flat leather texture with 3D cubes defined in `.bbmodel` files.

| File | Summary |
|---|---|
| `ArmorPart.kt` | Sealed class: 9 armor body parts (`Helmet`, `Chestplate`, `RightArm`, `LeftArm`, `InnerArmor`, `RightLeg`, `LeftLeg`, `RightBoot`, `LeftBoot`) with bone prefix, STASIS constant, layer, `cemYOffset` (CEM TOP-face Y correction), `isLeft`. `fromBoneName()` auto-detection |
| `ArmorDefinition.kt` | Data classes: `ArmorCube` (center, halfSize, rotation, pivot, uvFaces, emissive), `ArmorCubeUv`, `ParsedArmorPiece`, `ParsedArmor`, `RegisteredArmor` (id, colorId, RGB, parsed data) |
| `ArmorParser.kt` | Parses `.bbmodel` via `BlockbenchParser`, walks bone hierarchy, auto-detects armor pieces by prefix. Coordinate transform: BB → TBN space (bone-relative positioning) with per-part `cemYOffset` correction for TOP-face canvas origin. Left-part 180° mirror via sign multiplier. Handles nested prefixed sub-groups, multi-level rotations. Passes `lightEmission` (0-15) normalized to `emissive` (0.0-1.0) |
| `ArmorGlslGenerator.kt` | Converts parsed cubes → GLSL `ADD_BOX_WITH_ROTATION_ROTATE` macros. `generateArmorGlsl()` produces dual-section file (`#ifdef VSH`/`#ifdef FSH`). `generateArmorcordsGlsl()` produces RGB → armorId mapping. Emissive support: if any cube in a piece has `emissive > 0`, sets `dynamicEmissive = 1` in generated FSH — skips lighting, ColorModulator, and fog for that piece |
| `ArmorShaderPack.kt` | Assembles all shader pack entries: 16 static shader files from classpath + generated `armor.glsl` + `armorcords.glsl` + leather layer textures with marker pixels + transparent `leather_overlay.png` (suppresses vanilla overlay remnants). Returns `Map<String, ByteArray>` |
| `CustomArmorRegistry.kt` | Registry with `ConcurrentHashMap`. `loadFromResources(resources, dir)` auto-detects `.bbmodel` files. `register()` assigns color IDs via `ModelIdRegistry`. Extension functions: `createItem(ArmorPart)`, `equipFullSet(Player)` |
| `ArmorTestCommand.kt` | `/armor list` (show registered armors), `/armor equip <id>` (full set), `/armor give <id> <slot>` (single piece) |

**Bone prefix convention** (case-insensitive):
| Prefix | Part | STASIS | Layer |
|---|---|---|---|
| `h_` | Helmet | 199 | 1 |
| `c_` | Chestplate | 299 | 1 |
| `ra_` | Right Arm | 399 | 1 |
| `la_` | Left Arm | 499 | 1 |
| `ia_` | Inner Armor | 999 | 2 |
| `rl_` | Right Leg | 599 | 2 |
| `ll_` | Left Leg | 699 | 2 |
| `rb_` | Right Boot | 799 | 1 |
| `lb_` | Left Boot | 899 | 1 |

**How it works**: Each armor gets a unique RGB via `ModelIdRegistry`. Server equips leather armor dyed to that RGB. Vertex shader (`entity.vsh`) reads pixel `(63,31)` of the leather texture to identify custom armor — CEM mode is guarded by `IS_LEATHER_LAYER` so only leather textures trigger it (non-leather entities render normally). Fragment shader (`entity.fsh`) raycasts 3D cubes via `ADD_BOX` macros instead of rendering flat texture. Transparent `leather_overlay.png` files suppress vanilla overlay remnants (stitching, belt, kneeguards).

**Static shaders**: 16 files in `src/main/resources/shaders/armor/` from MC 1.21.6 overlay (compatible with 1.21.11). Key files: `entity.vsh`/`entity.fsh` (core pipeline), `frag_funcs.glsl` (CEM raycasting library), `armorparts.glsl` (STASIS constants), `setup.glsl` (UV-based part detection).

### Shader-Based HUD — `utils/hud/` (7 files)
Boss bar text with custom bitmap font sprites repositioned by modified `rendertype_text` vertex shader. Resolution/GUI scale independent via `ProjMat`-derived normalization. Coexists with map screen shaders.

| File | Summary |
|---|---|
| `shader/HudShaderPack.kt` | Generates combined `rendertype_text.vsh`/`.fsh` handling HUD positioning + map screen decoding. Reuses `MapShaderPack` for `map_decode.glsl` |
| `font/HudSprite.kt` | `HudSpriteDefinition` data class + `HudSpriteRegistry` singleton. Maps sprite IDs to PUA chars (U+E000+) and atlas grid positions. Pre-registers 34 sprites (bars, icons, borders, digits, glyphs). `registerFromImage()` for custom sprites |
| `font/HudFontProvider.kt` | Generates sprite atlas PNG (8×8 cells, 16 columns) + Minecraft bitmap font JSON (`minecraft:hud`). Placeholder sprites: colored rectangles for bars/icons, 5×7 pixel bitmaps for digits/glyphs |
| `Hud.kt` | Core types: `HudAnchor` (9 screen anchors), `Direction`, sealed `HudElement` hierarchy (`SpriteElement`, `BarElement`, `TextElement`, `GroupElement`, `AnimatedSpriteElement`), `HudLayout`. Builder DSL: `hudLayout("id") { bar(...) { }, sprite(...) { }, text(...) { }, group(...) { }, animated(...) { } }` |
| `HudManager.kt` | `PlayerHud` per-player-per-layout state class (values, groupItems, bossBar, lastRendered, animationTick). `HudManager` singleton: `register(layout)`, `show(player, layoutId)` (multi-layout — each layout gets its own boss bar, stackable), `hide(player, layoutId)`, `hideAll(player)`, `isShowing(player, layoutId)`, `update(player, elementId, value)` (auto-scans active layouts), `update(player, layoutId, elementId, value)` (explicit targeting), `addToGroup/removeFromGroup`, `install(eventNode)` (disconnect cleanup), `tick()` (render + diff + update boss bars) |
| `HudRenderer.kt` | Converts layout + state → Adventure `Component` for boss bar title. Position encoding: anchor + offset (0-1) → R=X×255, G=Y×255, B=254 marker. Each sprite character independently positioned via color. `Component.join()` combines all parts. Char step = 3/255 (~8 GUI pixels) |
| `HudExtensions.kt` | Player extensions: `showHud(layoutId)`, `hideHud(layoutId)`, `hideAllHuds()`, `isHudShowing(layoutId)`, `updateHud(elementId, value)`, `updateHud(layoutId, elementId, value)`, `addHudIcon/removeHudIcon`, `hud(layoutId): PlayerHud?`, `val huds`, `val activeHudIds` |

**How it works**: Server creates boss bar per layout per player (progress=0, overlay=PROGRESS — invisible bar). Title uses font `minecraft:hud` mapping PUA characters to sprite textures. Per-character vertex color encodes screen position (R=X%, G=Y%, B=254 marker). Modified vertex shader detects marker in GUI mode, repositions quad vertices to target screen coordinates derived from `ProjMat`. Fragment shader renders texture color only for HUD sprites (ignores vertex color encoding). Shadow vertices (B≈63, the 254×0.25 shadow darkening) are discarded — grayscale check (R≈G≈B) excludes white/gray text shadows to avoid false positives with real boss bars. High-blue text colors (aqua §b, blue §9, pink §d) may lose shadow due to B×0.25≈63 collision.

**Position encoding**: R channel → X fraction of GUI width (0-255). G channel → Y fraction of GUI height (0-255). B=254 → HUD marker. Resolution = 256×256 grid ≈ 3.75 GUI pixels per step at 960×540.

**GUI scale independence**: Shader derives GUI dimensions from orthographic `ProjMat`: `guiW = 2.0 / ProjMat[0][0]`, `guiH = -2.0 / ProjMat[1][1]`. Positions are percentage-based.

**Test command**: `/hud` — toggles test HUD layout (health bar, mana bar, compass icon, score, timer).

### Modified Existing Files
- `modelengine/generator/ModelIdRegistry.kt` — added `assignId(key: String)` single-arg overload, `getId(key: String)` overload
- `modelengine/generator/ModelGenerator.kt` — added `generateRaw()` returning `RawGenerationResult(blueprint, boneModels, textureBytes)`, `buildBoneElements()` and `buildFlatModel()` helpers. Bone offsets are parent-relative (not absolute). `buildBoneElements(centerOffset)`: ModelEngine bones use `centerOffset=0f` so element [0,0,0] maps to entity position (correct rotation pivot for item_display transforms); custom content flat models use default `centerOffset=8f` (standard MC model centering). All pack paths and `ITEM_MODEL` values lowercased for Minecraft resource location compliance. Bone items use `DataComponents.ITEM_MODEL` (`"minecraft:me_<model>_<bone>"` flat naming — all under `minecraft` namespace with `me_` prefix) instead of `CUSTOM_MODEL_DATA`. All pack assets (textures, models, items) placed under `assets/minecraft/` for guaranteed client-side resolution
- `modelengine/bone/BoneTransform.kt` — `toRelativePosition()` uses 3D Y-axis rotation matrix (not 2D rotation) to correctly position bones when the model has non-zero yaw
- `Orbit.kt` — calls `CustomContentRegistry.init()` before mode install, auto-merges pack (pack distribution delegated to proxy). Registers test HUD layout, `HudManager.install()`, HUD tick task (2 ticks), `/hud` test command
- `customcontent/CustomContentRegistry.kt` — `mergePack()` uses `HudShaderPack.generate()` + `HudFontProvider.generate()` instead of `MapShaderPack.generate()` directly (HudShaderPack internally generates map_decode.glsl via MapShaderPack)
- `customcontent/CustomContentCommand.kt` — removed `PackServer` and `/cc send` (pack sending delegated to proxy)

## Utility Framework — `utils/` (189 files)

### Model Engine (46)
| Util | Summary |
|---|---|
| `modelengine/ModelEngine.kt` | Singleton registry, factory DSL (`modeledEntity(entity) {}`, `modeledEntity(owner) {}`, `standAloneModel(pos) {}`), 1-tick loop via `install()`/`uninstall()`, blueprint CRUD, modeled entity lifecycle, `PlayerDisconnectEvent` session cleanup (viewer eviction, mount/VFX cleanup). `ModelOwner` interface decouples from Entity |
| `modelengine/math/ModelMath.kt` | `data class Quat(x,y,z,w)` with `operator get`/`toFloatArray()`, `eulerToQuat`/`quatToEuler` (gimbal-lock safe)/`quatSlerp`/`quatMultiply`/`quatRotateVec`/`quatInverse`/`quatNormalize`/`wrapDegrees`, `OrientedBoundingBox` (containsPoint/intersects/rayTrace), `clamp`/`lerp`/`lerpVec`/`colorArgb` |
| `modelengine/blueprint/ModelBlueprint.kt` | Immutable model data: bone tree, animations, hitbox dimensions. `traverseDepthFirst()` visitor. `AnimationBlueprint`, `BoneKeyframes`, `Keyframe`, `InterpolationType`, `LoopMode` |
| `modelengine/blueprint/BlueprintBone.kt` | Static bone definition: name, parent, children, offset, rotation, scale, modelItem, behaviors map, visibility |
| `modelengine/bone/BoneTransform.kt` | Immutable `BoneTransform(position, leftRotation, rightRotation, scale)`. `combine(parent)` chain, `toRelativePosition(yaw)` (bone offset rotated by model yaw — for display entity translation), `toWorldPosition(modelPos)` (absolute world coords — for behaviors), `toWorldRotation(yaw)`, `lerp()` |
| `modelengine/bone/ModelBone.kt` | Runtime mutable bone: local + animated transforms, `computeTransform()` recurses children, dirty-check. `behaviors` list, `addBehavior()`/`removeBehavior()`, inline `behavior<T>()`/`behaviorsOf<T>()` |
| `modelengine/render/BoneRenderer.kt` | Per-player `ITEM_DISPLAY` packet renderer. Entity IDs from `-3,000,000`. Spawn/update/destroy with dirty-checking (only changed metadata). Uses `toRelativePosition()` for META_TRANSLATION (relative to entity spawn). Teleports all bone entities to model position on movement via `EntityTeleportPacket`. `show`/`hide`/`update`/`destroy` |
| `modelengine/model/ActiveModel.kt` | Model instance from blueprint, owns bone map + renderer + `animationHandler: PriorityHandler`. Auto-creates behaviors from blueprint. Auto-plays first animation containing "idle" at init. `modelScale`, `computeTransforms()`, `tickAnimations(deltaSeconds)`, `playAnimation(name, lerpIn, lerpOut, speed)`, `stopAnimation(name)`, `stopAllAnimations()`, `isPlayingAnimation(name)`, `initBehaviors`/`tickBehaviors`/`destroyBehaviors`, `show`/`hide`/`destroy` |
| `modelengine/model/ModelOwner.kt` | `ModelOwner` interface (`position`, `isRemoved`, `ownerId`), `EntityModelOwner` adapter, `Entity.asModelOwner()` extension, `StandaloneModelOwner` (mutable position, no entity), `standAloneModel(pos) {}` DSL |
| `modelengine/model/ModeledEntity.kt` | Wraps `ModelOwner`, holds multiple ActiveModels, per-player viewer tracking, `entityOrNull` for Entity-specific downcast, `tick()` (animations → transforms → behaviors → renderer at 1/20s delta), `destroy()` calls behavior cleanup + unregisters from ModelEngine, `evictViewer()` propagates to behaviors. DSL builders: `ActiveModelBuilder.animation(name, lerpIn, lerpOut, speed)` |
| `modelengine/animation/AnimationHandler.kt` | Sealed interface: `play`/`stop`/`stopAll`/`isPlaying`/`tick` |
| `modelengine/animation/PriorityHandler.kt` | Priority-based per-bone blending. `boundModel` must be set before `play()`. Sorted priority iteration. Only resets animated bones (multi-layer safe). Weight lerp in/out |
| `modelengine/animation/StateMachineHandler.kt` | Layered state machines via `TreeMap<Int, AnimationStateMachine>`. Resets all bones once, then ticks all layers (additive override) |
| `modelengine/animation/AnimationStateMachine.kt` | States + conditional transitions. `animationStateMachine { state(); transition() }` DSL |
| `modelengine/animation/AnimationProperty.kt` | Per-bone animation state (pos/rot/scale), `blend()`, `fromKeyframes()` (positions divided by 16 for pixel→block conversion) |
| `modelengine/animation/KeyframeInterpolator.kt` | Binary-search keyframe evaluation with pluggable interpolation (LINEAR/CATMULLROM/BEZIER/STEP) |
| `modelengine/animation/InterpolationFunctions.kt` | `linearVec`, `stepVec`, `catmullromVec`, `bezierVec` |
| `modelengine/behavior/BoneBehavior.kt` | Sealed interface: `bone`, `onAdd`/`tick`/`onRemove`/`evictViewer` |
| `modelengine/behavior/HeadBehavior.kt` | Smooth look-at from `modeledEntity.headYaw/Pitch`, configurable maxPitch/maxYaw/smoothFactor |
| `modelengine/behavior/MountBehavior.kt` | Passenger seat via virtual Interaction entity. `mount(player)`/`dismount()`. Seat auto-teleports in tick. Seat IDs from `-3,500,000` |
| `modelengine/behavior/NameTagBehavior.kt` | Virtual TextDisplay at bone position. Reactive `text` property. Tag auto-teleports in tick. Tag IDs from `-3,600,000` |
| `modelengine/behavior/HeldItemBehavior.kt` | Override bone model item, restores on remove |
| `modelengine/behavior/GhostBehavior.kt` | Sets bone invisible, restores on remove |
| `modelengine/behavior/SegmentBehavior.kt` | Chain IK constraint: direction from parent, configurable angleLimit/rollLock |
| `modelengine/behavior/SubHitboxBehavior.kt` | OBB hitbox at bone position, scaled by transform, with damageMultiplier |
| `modelengine/behavior/LeashBehavior.kt` | Leash anchor delegating to `EntityLeashManager`, `attachTo(entity)`/`detach()` |
| `modelengine/behavior/PlayerLimbBehavior.kt` | Player skin on bone (HEAD/BODY/RIGHT_ARM/LEFT_ARM/RIGHT_LEG/LEFT_LEG). Limb IDs from `-3,700,000` |
| `modelengine/behavior/BoneBehaviorFactory.kt` | Creates behaviors from `BoneBehaviorType` enum + config map |
| `modelengine/interaction/ModelInteraction.kt` | Ray-cast against all model SubHitboxBehavior OBBs. `raycast(player)`/`raycastAll(player)` → `ModelHitResult` |
| `modelengine/interaction/ModelDamageEvent.kt` | Custom event: attacker, modeledEntity, bone, hitbox, hitDistance, damage, cancelled |
| `modelengine/mount/MountManager.kt` | Orchestrates driver/passenger sessions. Captures `ClientInputPacket` for WASD/jump. Auto-dismount on sneak |
| `modelengine/mount/MountController.kt` | Sealed interface: `tick(modeledEntity, driver, input)`. `MountInput(forward, sideways, jump, sneak)` |
| `modelengine/mount/WalkingController.kt` | Ground movement from player input. Configurable speed/jumpVelocity |
| `modelengine/mount/FlyingController.kt` | Flying movement from player input. Configurable speed/verticalSpeed |
| `modelengine/vfx/VFX.kt` | Standalone ItemDisplay effect. `vfx(item) { position(); scale(); lifetime() }` DSL. Per-player show/hide. VFX IDs from `-3,800,000` |
| `modelengine/vfx/VFXRegistry.kt` | Lifecycle management, 1-tick loop, auto-remove on lifetime expiry |
| `modelengine/lod/LODLevel.kt` | `lodConfig { level(16.0, tickRate=1); cullDistance(64.0) }` DSL. Per-level visible/hidden bone sets |
| `modelengine/lod/LODHandler.kt` | Closest-viewer distance evaluation. Applies bone visibility per LOD level. Per-player culling beyond max distance |
| `modelengine/advanced/RootMotion.kt` | Extracts root bone animation delta, applies to entity position. Configurable X/Y/Z axes |
| `modelengine/advanced/ModelSerializer.kt` | Save/load ModeledEntity state as ByteArray. Versioned binary format |
| `modelengine/generator/BlockbenchModel.kt` | Data classes: `BbElement` (includes `lightEmission` for PBR emissive), `BbGroup`, `BbFace`, `BbTexture`, `BbAnimation`, `BbKeyframe` |
| `modelengine/generator/BlockbenchParser.kt` | Parses `.bbmodel` JSON → `BlockbenchModel`. Handles outliner hierarchy, textures, animations |
| `modelengine/generator/ModelGenerator.kt` | Orchestrator: parse `.bbmodel` → register `ModelBlueprint` + generate resource pack zip. Bone items use `ITEM_MODEL` component (`"minecraft:me_<model>_<bone>"` flat naming under `minecraft` namespace) for direct model resolution. All pack assets placed under `assets/minecraft/` (textures, models, items) with `me_` prefix to avoid collisions |
| `modelengine/generator/AtlasManager.kt` | Texture atlas stitching from base64-encoded bbmodel textures. Preserves original index order. Power-of-2 dimensions. `entryByOriginalIndex()` for UV lookup |
| `modelengine/generator/ModelIdRegistry.kt` | Persistent custom model data ID assignment. `computeIfAbsent`-based atomic assignment. Atomic file write via temp+rename |
| `modelengine/generator/PackWriter.kt` | Writes `pack.mcmeta`, model JSONs, textures into zip. Configurable pack format |

### Spatial (1)
| Util | Summary |
|---|---|
| `blockindex/BlockPositionIndex.kt` | Per-instance spatial index for specific block types. Eliminates O(n³) cubic scans. `BlockPositionIndex(targetBlockNames, eventNode).install()`, `positionsNear(instance, center, radius)` O(k), `allPositions(instance)`, `scanChunk(instance, chunk, minY, maxY)`, `evictInstance(hash)`. Coordinate packing: `(x << 40) \| ((y & 0xFFFFF) << 20) \| (z & 0xFFFFF)`. Auto-tracks via `PlayerBlockPlaceEvent`/`PlayerBlockBreakEvent` on scoped eventNode. |

### Display (12)
| Util | Summary |
|---|---|
| `gui/Gui.kt` | `gui(title, rows) {}` DSL, paginated GUI, event-based click handling, atomic event node cleanup via `AtomicBoolean` guard (prevents double-removal on close+disconnect race) |
| `scoreboard/Scoreboard.kt` | `scoreboard(title) { line(); animatedLine(); dynamicLine {} }` DSL, `PerPlayerScoreboard`, `AnimatedScoreboard` (immutable frame lists, `@Volatile` sidebar/tickTask), `TeamScoreboard`, `ObjectiveTracker`, `liveScoreboard { title(); line(); refreshEvery() }` auto-managed lifecycle DSL, show/hide/update |
| `hologram/Hologram.kt` | TextDisplay holograms, `Instance.hologram {}` global + `Player.hologram {}` packet-based per-player, DSL builder, billboard/scale/background |
| `bossbar/BossBarManager.kt` | `bossBar(name, color, overlay) {}` DSL, show/hide/update |
| `tablist/TabList.kt` | `Player.tabList { header(); footer() }` DSL, `liveTabList { header(); footer(); refreshEvery() }` auto-managed lifecycle DSL |
| `actionbar/ActionBar.kt` | `Player.showActionBar(msg, durationMs)`, `clearActionBar()` |
| `title/Title.kt` | `Player.showTitle { title(); subtitle(); fadeIn(); stay(); fadeOut() }` DSL |
| `healthdisplay/HealthDisplay.kt` | `healthDisplay { format { } }` DSL, periodic display name health suffix updates |
| `entityglow/EntityGlow.kt` | Per-player entity glow via `EntityMetaDataPacket` (no real entities), 20-tick metadata refresh, `Player.setGlowingFor()`, global glow, timed glow |
| `notification/Notification.kt` | `notify(player) { title(); message(); channels(CHAT, ACTION_BAR, TITLE, SOUND) }` DSL, `NotificationManager` broadcast/instance/player, multi-channel (CHAT, ACTION_BAR, TITLE, BOSS_BAR, SOUND), `announceChat()`, `announceActionBar()`, `announceTitle()` convenience functions |
| `playertag/PlayerTag.kt` | `playerTag(player) { prefix(); suffix(); nameColor(); priority() }` DSL, `PlayerTagManager`, priority-based tag stacking, display name/tab/above-head rendering |
| `clickablechat/ClickableChat.kt` | `clickableMessage(player) { text(); clickText("[HERE]") { action(OPEN_URL, url) }; hover() }` DSL, `Player.sendClickable {}`, RUN_COMMAND/SUGGEST_COMMAND/OPEN_URL/COPY_TO_CLIPBOARD actions |
| `blockhighlight/BlockHighlight.kt` | `BlockHighlightManager` glowing invisible shulker at block position, `Player.highlightBlock(x, y, z, durationTicks)`, `Player.clearHighlights()`, auto-remove after duration |
| `nametag/NameTag.kt` | `Player.setNameTag { prefix(); suffix(); displayName() }` DSL, `NameTagManager` per-player custom name via prefix+name+suffix Component, `Player.clearNameTag()` |
| `playerlist/PlayerList.kt` | `playerList { header(); footer(); updateIntervalTicks }` DSL, `PlayerListManager` periodic per-player header/footer updates, MiniMessage support |

### Game Framework (21)
| Util | Summary |
|---|---|
| `gamestate/GameState.kt` | `gameStateMachine(initialState) { allow(); onEnter(); timedTransition() }` DSL |
| `arena/Arena.kt` | `arena(name) { instance(); region(); spawn() }` DSL, `ArenaRegistry` |
| `queue/Queue.kt` | `gameQueue(name) { minPlayers(); maxPlayers(); onStart() }` DSL, `QueueRegistry`, `simpleQueue(name)` FIFO variant, `Player.joinQueue/leaveQueue/queuePosition` extensions |
| `mappool/MapPool.kt` | `mapPool(name) { map(); strategy() }` DSL, voting, RANDOM/ROTATION/VOTE |
| `kit/Kit.kt` | `kit(name) { item(); helmet(); chestplate() }` DSL, `KitRegistry`. String-key overloads (`helmet("ruby_helmet")`, `item(0, "ruby_sword")`) resolve via `ItemResolver` — supports both custom content IDs and `minecraft:material` keys |
| `loot/LootTable.kt` | `lootTable(name) { entry(material, weight); entry(key, weight); rolls() }` DSL, string-key entries resolved via `ItemResolver` |
| `vote/Vote.kt` | `poll(question) { option(); durationTicks(); onComplete() }` DSL |
| `lobby/Lobby.kt` | `lobby { instance; spawnPoint; hotbarItem() }` DSL, protection (blocks/damage/hunger/inventory), void teleport, `lockInventory` cancels InventoryPreClickEvent/ItemDropEvent/PlayerSwapItemEvent |
| `achievement/Achievement.kt` | `achievement(id) { name; category; maxProgress; frameType }` DSL, `AchievementRegistry` with Gravity `AchievementStore` persistence, 6 categories (GENERAL/COMBAT/SURVIVAL/SOCIAL/EXPLORATION/MASTERY), `loadPlayer/unloadPlayer` lifecycle, `progress/complete` atomic via `IncrementAchievementProcessor/SetAchievementCompletedProcessor`, vanilla advancement toast unlock via `player.sendNotification()`, `AchievementTriggerManager` stat-based auto-triggers with `bindThreshold/evaluate` |
| `graceperiod/GracePeriod.kt` | `gracePeriod(name) { duration(); cancelOnMove(); cancelOnAttack(); onEnd {} }` DSL, `GracePeriodManager` (`@Volatile` eventNode/cleanupTask), invulnerability management |
| `condition/Condition.kt` | `condition { hasPermission("x") and isAlive() and not(isFrozen()) }` DSL, composable `Condition<Player>` with `and`/`or`/`not`/`xor`, built-in conditions |
| `stattracker/StatTracker.kt` | `StatTracker.increment(player, "kills")`, `statTracker { stat(); derived("kdr") {} }` DSL, ConcurrentHashMap storage, top-N leaderboard, derived stats, `renderLeaderboard()`, `Player.sendLeaderboard()`, `LeaderboardRegistry` |
| `roundmanager/RoundManager.kt` | `roundManager(name) { rounds(); roundDuration(); onRoundStart {}; onGameEnd {} }` DSL, round/intermission state machine, per-round scores |
| `matchresult/MatchResult.kt` | `matchResult { winner(); losers(); stat("kills") {}; mvp() }` DSL, `MatchResultDisplay` rendered summary, `MatchResultManager` history tracking |
| `areaeffect/AreaEffect.kt` | `areaEffect(name) { region(); instance(); effect(); interval(); onEnter {}; onExit {} }` DSL, `AreaEffectManager`, timer-based region scanning, auto-remove on exit |
| `combatarena/CombatArena.kt` | `combatArena(name) { instance(); spawnPoints(); maxPlayers(); kit(); duration(); onKill {}; onEnd {} }` DSL, `CombatArenaManager`, kill/death/damage tracking, `ArenaResult` |
| `minigametimer/MinigameTimer.kt` | `minigameTimer(name) { duration(); display(BOSS_BAR/ACTION_BAR/TITLE); onTick {}; onHalf {}; onQuarter {}; onEnd {} }` DSL, start/pause/resume/stop, addTime/removeTime, milestone callbacks |
| `taskqueue/TaskQueue.kt` | `taskQueue(name) { maxConcurrent(1); onComplete {}; onError {} }` DSL, `TaskQueue.submit {}`, sequential/limited-concurrency async execution on virtual threads, pause/resume/stop, `TaskQueueRegistry` |
| `teambalance/TeamBalance.kt` | `TeamBalance.balance(players, teamCount, scorer)`, snake-draft distribution, `suggestSwap()` variance minimization, `autoBalance()` |
| `knockback/Knockback.kt` | `KnockbackProfile(horizontal, vertical, extraHorizontal, extraVertical, friction)`, `KnockbackManager` per-player profile overrides, `Entity.applyKnockback(source, profile)`, `Entity.applyDirectionalKnockback()`, `knockbackProfile(name) {}` DSL |
| `respawn/Respawn.kt` | `RespawnManager` per-player and default respawn points, `Player.setRespawnPoint()`, `Player.clearRespawnPoint()`, `Player.getCustomRespawnPoint()`, instance resolution by identity hash |
| `instancepool/InstancePool.kt` | `instancePool(name, poolSize) { factory }`, `InstancePool` with `acquire()`/`release()`, auto-warmup pool, excess unregistration, `availableCount`/`inUseCount`/`totalCount` |
| `timer/Timer.kt` | `gameTimer(name) { durationSeconds(); onTick {}; onComplete {}; display {} }` DSL, `GameTimer` with start/stop/reset, per-player viewer tracking, action bar display |

### World (15)
| Util | Summary |
|---|---|
| `world/WorldManager.kt` | Named instance management, `create(name) { generator(); flat(); void() }` DSL |
| `chunkloader/ChunkLoader.kt` | `ChunkLoader.loadRadius()`, `preloadAroundSpawn()`, `unloadOutsideRadius()` |
| `worldborder/WorldBorder.kt` | `instance.managedWorldBorder { diameter(); center() }` DSL, `shrinkTo()`, `expandTo()` |
| `worldreset/WorldReset.kt` | `resetChunks()`, `clearArea()`, `fillArea()`, `recreateInstance()` |
| `anvilloader/AnvilWorldLoader.kt` | `AnvilWorldLoader.load(name, path)` (validates region/ + .mca), `loadAndPreload(name, path, centerChunkX, centerChunkZ, radius)`, `verifyLoaded(instance, pos)` |
| `worldgenerator/WorldGenerator.kt` | `layeredGenerator {}` DSL, `flatGenerator()`, `voidGenerator()`, `checkerboardGenerator()` |
| `mapgen/Noise.kt` | `PerlinNoise(seed)` 2D/3D, `OctaveNoise` fBm, `RidgedNoise` ridges, `WarpedNoise` domain warping, `NoiseSource2D` interface, `ScaledNoise` |
| `mapgen/Biome.kt` | `BiomeDefinition` (21 fields incl. visual colors), `BiomeRegistry` (16 defaults, `registerMinestomBiomes()` for F3 + client visuals), `BiomeProvider` (temp+moist+weird selection, zones, exclusion, blending), `BiomeZoneConfig` |
| `mapgen/TerrainGenerator.kt` | Minestom `Generator`: 7-layer noise, continental/erosion, rivers, overhangs, 8 height curves (LINEAR/SMOOTH/TERRACE/AMPLIFIED/CLIFF/RIDGED/MESA/ROLLING), biome-blended heightmap, deepslate layer, beaches, badlands terracotta bands, heightmap cache |
| `mapgen/CaveGenerator.kt` | `CaveCarver`: Perlin worm caves, ravines (ellipsoid), cave rooms, lava fill, `decorateAll()` (aquifers, moss, dripstone, glow lichen, hanging roots), `CaveConfig` presets (vanilla/dense/sparse/none) |
| `mapgen/OreGenerator.kt` | `OrePopulator`: 8 ore types, elongated vein placement, per-ore + biome multipliers, `OreConfig` presets (vanilla/boosted/none) |
| `mapgen/TerrainModifier.kt` | `TerrainModifier`: ice/snow/surface patches/underwater clay/ice spikes, `flattenArea()` for structures, `ModifierConfig` |
| `mapgen/MapPopulator.kt` | 6 tree types (with spacing), boulders, ponds, mushrooms, biome-aware vegetation, tall plants, fallen logs, underwater vegetation (kelp/seagrass), sugar cane, cactus, lily pads, `PopulationConfig` |
| `mapgen/SchematicPopulator.kt` | `SchematicPopulator`: terrain-adaptive `.schem` placement (SURFACE/UNDERGROUND/EMBEDDED), rotation, foundation fill, cavity carving, entrance generation, placeholder→chest replacement, lootTableId per def |
| `mapgen/BattleRoyaleMapGenerator.kt` | `BattleRoyaleMapGenerator.generate(config)` → `GeneratedMap`, full pipeline (biomes→terrain→caves→ores→modifiers→population→cave decoration→schematics→loot) |
| `mapgen/MapPresets.kt` | `MapPresets[name]` → `MapGenerationConfig`. Programmatic presets bypassing Gson serialization issues. `"perfect"` / `"battleroyale"` preset with seed-based randomization: map radius (200-300), sea level (60-65), cave frequency/density (±30%), ore amounts (±20-50%), population chances (±30%), terrain parameters (±20%). Deterministic when given a seed |
| `biome/CustomBiome.kt` | `customBiome(id) { blocks {}; terrain {}; climate {}; vegetation {}; visuals {}; modifiers {} }` DSL, scoped builders, `TerrainShape` enum (20 presets: FLAT/PLAINS/ROLLING_HILLS/ROLLING/HIGHLANDS/FOOTHILLS/PLATEAUS/MESA/MOUNTAINOUS/RIDGED/PEAKS/SPIRES/VALLEYS/BASIN/CANYON/CLIFFS/ERODED/DUNES/OCEAN_FLOOR/SHELF) via `terrain { shape(TerrainShape.X) }`, `BiomePresets` object (volcanic, mushroomFields, frozenWasteland, lushCaves, cherryGrove, deepDark), `BiomePresets.all()` |
| `schematic/Schematic.kt` | Sponge v2 schematic loader, `schematic.paste(instance, origin)` |
| `region/Region.kt` | Sealed `Region` (Cuboid/Sphere/Cylinder), `RegionManager`, spatial queries |
| `boundary/Boundary.kt` | `boundary(name) { cuboid(); circle(); onBlocked() }` DSL, `BoundaryManager`, invisible wall system |
| `weathercontrol/WeatherControl.kt` | `instanceWeather(instance) { rainy(); duration() }` DSL, `WeatherController`, per-instance weather |
| `blocksnapshot/BlockSnapshot.kt` | `Instance.captureSnapshot(region)`, `BlockSnapshot.capture/restore/restoreAsync/diff/pasteAt/createInstance`, `Instance.blockRestore {}` DSL with auto-restore timer, packed XYZ storage |
| `selectiontool/SelectionTool.kt` | `SelectionManager`, `Player.setPos1/setPos2`, `Player.getSelection()`, wand item, particle boundary display, `Player.fillSelection(block)`, `countBlocks()` |
| `randomteleport/RandomTeleport.kt` | `randomTeleport(player) { minX(); maxX(); minZ(); maxZ(); instance(); maxAttempts(); safeCheck() }` DSL, `Player.randomTeleport {}` extension, safe location finding |
| `blockpalette/BlockPalette.kt` | `blockPalette(name) { block(Block.X, weight) }` DSL, weighted random block selection, `fillRegion()`, `Instance.fillWithPalette()` |
| `fallingblock/FallingBlockUtil.kt` | `spawnFallingBlock(instance, position, block, velocity, onLand)` with ground detection and 600-tick auto-remove, `launchBlock(instance, position, block, direction, speed)` directional launch |
| `structureblock/StructureBlock.kt` | `Structure(name, blocks)` with `paste()`, `pasteRotated90()`, `clear()`, `captureStructure(name, instance, from, to)` region capture, `StructureRegistry` CRUD |
| `voidteleport/VoidTeleport.kt` | `voidTeleport { threshold(-64.0); destination { player -> pos }; onTeleport {} }` DSL, `VoidTeleportManager` global `PlayerMoveEvent` listener, configurable Y threshold, respawnPoint default destination |

### Player (17)
| Util | Summary |
|---|---|
| `snapshot/InventorySnapshot.kt` | `InventorySnapshot.capture(player)` / `snapshot.restore(player)` |
| `combatlog/CombatLog.kt` | `CombatTracker`, `Player.isInCombat`, `tagCombat()` |
| `afk/AfkDetector.kt` | Movement/chat tracking, configurable threshold, AFK/return callbacks |
| `freeze/Freeze.kt` | `FreezeManager`, `Player.freeze()`, `Player.isFrozen` |
| `vanish/Vanish.kt` | `VanishManager`, `Player.vanish()`, packet-level hiding |
| `spectate/Spectate.kt` | `SpectateManager`, `Player.spectatePlayer()`, restores previous game mode |
| `compass/CompassTracker.kt` | `CompassTracker.track()`, action bar direction/distance, `Player.trackPlayer()` |
| `trail/Trail.kt` | `TrailManager`, `TrailConfig(particle, count)`, `Player.setTrail()` |
| `deathmessage/DeathMessage.kt` | `deathMessages { pvp(); fall(); void(); generic() }` DSL, damage type tracking via Tag, placeholder replacement, broadcast scope |
| `warmup/Warmup.kt` | `warmup(player, "Teleporting", 5.seconds) { cancelOnMove(); cancelOnDamage(); onComplete {} }` DSL, `WarmupManager`, action bar progress, cancel triggers (MOVE, DAMAGE, COMMAND) |
| `velocityhelper/VelocityHelper.kt` | `Player.launchUp()`, `launchForward()`, `launchToward()`, `knockbackFrom()`, `Entity.freeze()`, `calculateParabolicVelocity()` |
| `inventoryserializer/InventorySerializer.kt` | `InventorySerializer.serialize(player): ByteArray`, `deserialize(player, data)`, `Player.serializeInventory()`, `Player.deserializeInventory()` extensions |
| `playervault/PlayerVault.kt` | `playerVault { maxVaults(); rows(); titleFormat() }` DSL, `open(player, vaultId)`, `getVault()`, per-player vault storage |
| `inventoryutil/InventoryUtil.kt` | Player inventory extensions: `hasItem()`, `countItem()`, `removeItem()`, `giveItem()`, `firstEmptySlot()`, `clearInventory()`, `sortInventory()`, `swapSlots()` |
| `playerdata/PlayerData.kt` | `PlayerDataManager` full player state snapshots (position, health, food, gameMode, level, inventory), `Player.captureData()`, `Player.restoreData()`, `Player.restoreLatest()`, timestamped history |
| `signprompt/SignPrompt.kt` | `SignPromptManager` sign-based text input prompts, `Player.openSignPrompt(lines) { player, lines -> }`, callback-driven response handling, cancel support |
| `waypoint/Waypoint.kt` | `WaypointManager` global and per-player named waypoints with position/instance/icon, `Player.setWaypoint()`, `Player.removeWaypoint()`, `Player.getWaypoint()`, `Player.allWaypoints()` |

### Advanced (10)
| Util | Summary |
|---|---|
| `commandalias/CommandAlias.kt` | `registerAlias("tp", "teleport")`, `registerAliases()` batch |
| `entityeffect/EntityEffect.kt` | `entityEffect { entityType; durationTicks; onSpawn; onTick; onRemove }` DSL, `spawnTemporaryEntity()` |
| `permissions/Permissions.kt` | `PermissionManager`, group hierarchy with inheritance, `Player.hasOrbitPermission()` |
| `pathfinding/Pathfinding.kt` | A* pathfinding, `Pathfinder.findPath(instance, start, end)`, configurable max iterations |
| `replay/Replay.kt` | 7 frame types (Position/BlockChange/Chat/ItemHeld/EntitySpawn/EntityDespawn/Death), `ReplayRecorder` with player join/leave/death tracking + skin data, `ReplayPlayer` with speed control (`setSpeed`/`pause`/`resume`/`seekTo`), perspective switching (`setPerspective`/`availablePerspectives`), progress tracking, completion callback, `ReplayManager` in-memory storage, `ReplayStorage` GZIP-compressed JSON persistence via `StorageScope` (`initialize(scope)`, `save`/`load`/`delete`/`list`/`exists`), custom Gson serializer for sealed `ReplayFrame`. Wired into `GameMode` lifecycle: recording starts in `enterPlaying()`, stops and persists in `enterEnding()` |
| `entityspawnerpool/EntitySpawnerPool.kt` | `entityPool(EntityType.ZOMBIE, poolSize) { onAcquire {}; onRelease {} }` DSL, `EntityPool.acquire()/release()`, auto-expand, recycled entities |
| `entitycleanup/EntityCleanup.kt` | `entityCleanup(instance) { maxAge(); maxPerInstance(); excludeTypes(); warningMessage() }` DSL, `EntityCleanupManager`, timer-based cleanup, item-first priority, per-instance config |
| `entitymount/EntityMount.kt` | `EntityMountManager.mount(rider, vehicle)`, `dismount()`, `mountStack()`, `mountConfig { speedMultiplier(); jumpBoost(); steeringOverride() }` DSL, `Player.mountEntity()`/`dismountEntity()` extensions |
| `entityleash/EntityLeash.kt` | `EntityLeashManager.leash(entity, holder, maxDistance)`, `unleash()`, velocity correction scheduler, `LeashHandle`, `Entity.leashTo()`, `Entity.unleash()`, `Entity.isLeashed()`, `Entity.leashHolder()` |
| `entitystack/EntityStack.kt` | `StackedEntity(base, riders)` with `addRider()`/`removeTop()`/`removeAll()`/`despawn()`, `EntityStackManager` singleton with `createStack(instance, position, baseType)`, `getStack()`, `removeStack()`, `all()`, `clear()` |

### Infrastructure (26)
| Util | Summary |
|---|---|
| `cooldown/Cooldown.kt` | Generic `Cooldown<K>` with `isReady/use/tryUse/remaining/reset/cleanup`, `NamedCooldown` (player+key with warning message), `MaterialCooldown` (player+material), `SkillCooldown` (visual indicator via BossBar/ActionBar), `Player.isOnCooldown/useCooldown/cooldownRemaining` extensions |
| `spawnentity/SpawnEntity.kt` | `spawnEntity(EntityType.X) { position(); instance(); customName(); health(); onSpawn {} }` DSL, `Instance.spawnEntity()` extension |
| `countdown/Countdown.kt` | `countdown(seconds) { onTick(); onComplete() }` DSL |
| `scheduler/Scheduler.kt` | `delay(ticks)`, `repeat(ticks)`, `delayedRepeat()`, `repeatingTask {}` DSL |
| `itembuilder/ItemBuilder.kt` | `itemStack(material) { name(); lore(); unbreakable(); glowing() }` DSL |
| `itemresolver/ItemResolver.kt` | `ItemResolver.resolve(key, amount)` — checks `CustomItemRegistry` first, then `Material.fromKey()`. `resolveMaterial(key)`, `isCustom(stack)`, `customId(stack)`. Tags custom items with `ITEM_ID_TAG`. KitBuilder string overloads delegate here |
| `itemmechanic/ItemMechanic.kt` | `ItemMechanic` interface (onUse/onAttack/onHurt/onBlockBreak), `ItemMechanicRegistry`, `ItemMechanicListener` global event node, `itemMechanic(id) { onUse {}; onAttack {} }` DSL |
| `teleport/Teleport.kt` | `TeleportManager`, warmup teleport with move cancel |
| `particle/Particle.kt` | `particleEffect(type, count, offset) {}` DSL, shapes (Circle, Sphere, Helix, Line, Cuboid), `Instance.spawnParticle/spawnParticleLine/spawnParticleCircle/spawnBlockBreakParticle` (uses `sendGroupedPacket` for batched delivery), `Player.showParticleShape {}` DSL |
| `sound/Sound.kt` | `soundEffect(type, source, volume, pitch) {}` DSL |
| `team/Team.kt` | `TeamManager.create(name) {}` DSL, `Player.joinTeam()` |
| `npc/Npc.kt` | Packet-based fake NPCs with `NpcVisual` sealed interface: `SkinVisual` (player skin), `EntityVisual` (any EntityType + raw metadata), `ModelVisual` (model-only, invisible INTERACTION hitbox). `npc(name) { skin(); entityType(); modelOnly(); metadata(); model {} }` DSL, configurable `nameOffset`, TextDisplay name, per-player visibility, optional `StandaloneModelOwner` for Blockbench model attachment (visibility synced), `Instance.spawnNpc()`, `Player.showNpc/hideNpc()` |
| `chat/Chat.kt` | `mm(text)`, `Player.sendMM()`, `Instance.broadcastMM()`, `message {}` builder |
| `placeholder/Placeholder.kt` | `PlaceholderRegistry`, `Player.resolvePlaceholders(text)` |
| ~~`eventbus/EventBus.kt`~~ | Removed — redundant with Minestom's `EventNode` system |
| `metadata/EntityMetadata.kt` | `Entity.setString/getInt/setFloat/...` Tag shortcut extensions, `EntityPropertyRegistry` typed property system, `Entity.setProperty<T>/getProperty<T>/removeProperty/hasProperty/propertyKeys` |
| `entitytracker/EntityTracker.kt` | `Player.nearestPlayer()`, `Player.nearestEntity()`, `Instance.entitiesInLine()` (use Minestom's `Instance.getNearbyEntities()` for radius queries) |
| `protection/Protection.kt` | Unified protection: sealed `ProtectionZone` (RegionZone/ChunkZone/RadiusZone), `ProtectionFlag` enum (BREAK/PLACE/INTERACT/PVP/MOB_DAMAGE), `ProtectionManager` with single EventNode, `protectRegion {}`, `protectChunk {}`, `protectSpawn {}` DSL |
| `damage/Damage.kt` | `DamageTracker` per-player damage history, `DamageIndicator` floating TextDisplay damage numbers, `DamageMultiplierManager` per-player per-source multipliers, `Player.setDamageMultiplier/getDamageMultiplier/removeDamageMultiplier` extensions |
| `raytrace/RayTrace.kt` | `rayTraceBlock()`, `rayTraceEntity()`, `raycast()` (entity+block in one pass with bounding box checks), `Player.lookDirection/rayTraceBlock/rayTraceEntity/lookingAt/getLookedAtEntity/getLookedAtBlock` extensions |
| `hotbar/Hotbar.kt` | `hotbar(name) { slot(0, item) { ... } }` DSL, apply/remove/install |
| `portal/Portal.kt` | `portal(name) { region(); destination }` DSL, `PortalManager` |
| ~~`resourcepack/`~~ | Removed — pack sending delegated to proxy |
| `animation/Animation.kt` | `blockAnimation {}` / `entityAnimation {}` DSL, keyframes, interpolation, `packetOnly` mode for visual-only block animations via `BlockChangePacket` |
| `inventorylayout/InventoryLayout.kt` | `inventoryLayout { border(); slot(); pattern(); centerItems() }` DSL |
| `entityai/EntityAI.kt` | `creature.configureAI { hostile(); passive(); neutral() }` DSL, AI presets |
| `joinleavemessage/JoinLeaveMessage.kt` | `joinLeaveMessages { joinFormat(); leaveFormat() }` DSL, MiniMessage, uninstallable handle |
| `blockbreakanimation/BlockBreakAnimation.kt` | `Instance.animateBlockBreak(position, ticks)`, progressive 0-9 crack stages |
| `fireworkdisplay/FireworkDisplay.kt` | `fireworkShow(instance) { at(0) { launch(pos) { } } }` DSL, timed launches |
| `tpsmonitor/TPSMonitor.kt` | `TPSMonitor.install()`, `currentTPS`, `averageTPS`, `mspt`, 1200-tick ring buffer |
| `mobspawner/MobSpawner.kt` | `spawnMob(EntityType.ZOMBIE) { instance(); position(); health(); drops {}; equipment {} }` DSL, `waveSpawner { wave(1) { mob() } }` DSL, `MobSpawnerPoint` periodic spawning |
| `podium/Podium.kt` | `podium(instance) { first(); second(); third(); displayDuration() }` DSL, pedestal blocks, firework, auto-cleanup |
| `coinflip/CoinFlip.kt` | `coinFlip { onHeads {}; onTails {} }`, `diceRoll(sides) {}`, `weightedRandom<T> {}` DSL, animated reveal |
| `spectatorcam/SpectatorCam.kt` | `spectatorCam(player) { target(); mode(FIRST_PERSON/FREE_CAM/ORBIT) }` DSL, orbit camera, player cycling |
| `cinematic/CinematicCamera.kt` | `cinematic(player) { node(time, pos); lookAt(entity); loop(); onComplete {} }` DSL, keyframe path with catmull-rom/bezier/linear position interpolation, quaternion slerp rotation, dynamic lookAt target tracking, reuses `KeyframeInterpolator` + `quatSlerp` |
| `screen/Screen.kt` | `screen(player, eyePos) { cursor {}; background(color); onDraw { canvas -> }; button(id, x, y, w, h) { onClick {}; onHover {} }; panel(x, y, w, h, color) { label(); button(); progressBar(); image() } }` DSL, shader-decoded map-based renderer: MSB-split encoding into item frame map grid (640x384 default = 10x6 maps, 64x64 true-color pixels per map), GLSL palette reverse-lookup decode in `rendertype_text.fsh`+`entity.fsh`, `MapCanvas` pixel buffer with BitSet tile-level dirty tracking, `MapEncoder` tile encoding with magic signature + partial dirty-only encode, `MapDisplay` packet-based item frame grid with row-level partial `MapDataPacket` updates (diffs previous data, sends only changed row ranges), staggered initial load (20 tiles/tick), `ScreenConfig.kt` auto-depth projection with `require` validation, ITEM_DISPLAY cursor with interpolation, AABB hit-testing + widget tree hit-testing, camel mount input capture, `MapShaderPack.kt` generates 128-entry palette GLSL, `TextureLoader` (classpath/bytes/BufferedImage → `Texture` with scaling/sub-region), drawing primitives (`line`/`circle`/`filledCircle`/`roundedRect`/`stroke`/`linearGradient`/`radialGradient`/`blendPixel`), `BitmapFont` grid-atlas font system with built-in 6x8 default, `AnimationController` per-session tween system (`Easing.LINEAR/EASE_IN/EASE_OUT/EASE_IN_OUT`, `IntInterpolator`/`DoubleInterpolator`/`ColorInterpolator`), composable widget tree (`Panel`/`Label`/`Button`/`ProgressBar`/`ImageWidget`) with auto-draw and auto-hit-test |
| `hud/Hud.kt` | Shader-based HUD system: boss bar text with bitmap font sprites repositioned by modified `rendertype_text` vertex shader. `hudLayout("id") { bar(); sprite(); text(); group(); animated() }` DSL, `player.showHud/hideHud/updateHud` extensions, per-player state via `HudManager`, 2-tick render loop with diff-only boss bar updates. See `utils/hud/hud.md` |
| `chestloot/ChestLoot.kt` | `chestLoot(name) { tier("common") { item() }; fillChestsInRegion() }` DSL, weighted tiers, amount ranges via `LootItem(baseItem: ItemStack)`, items resolved via `ItemResolver` (custom content + vanilla), `maxPerChest` per item (caps duplicate appearances in one chest, filtered during roll with weight redistribution), `fillChestAt(table, x, y, z)`, `LootMode.GLOBAL` (shared) / `LootMode.PER_PLAYER` (lazy per-UUID generation), `getChestInventory(x, y, z, playerId)`, season-scoped via `Season.lootTables` (registered in `BattleRoyaleMode.init`, cleared on reset). **Loot zones**: `lootZone(name) { cylinder(center, radius); tier("common", 100) }` DSL, `ChestLootManager.registerZone(zone)` — region-based tier weight overrides, first matching zone wins (register inner→outer), applied at `fillChestAt`/`getChestInventory` time, cleared on `clear()`/`clearZones()` |
| `npcdialog/NPCDialog.kt` | `npcDialog(npcName) { page("greeting") { text(); option("quest") {} } }` DSL, tree-structured dialog, clickable chat options |
| `autorestart/AutoRestart.kt` | `autoRestart { after(6.hours); warnings(30.minutes, 10.minutes); warningMessage(); onRestart {} }` DSL, `AutoRestartManager.scheduleRestart/cancelRestart/getTimeRemaining`, broadcast warnings, kick on restart |
| `customrecipe/CustomRecipe.kt` | `shapedRecipe(result) { pattern(); ingredient() }`, `shapelessRecipe {}`, `smeltingRecipe()`, `RecipeRegistry` matching, `RecipeHandle` unregistration |
| `entityequipment/EntityEquipment.kt` | `Entity.equip { helmet(); chestplate(); mainHand() }` DSL, `Entity.clearEquipment()`, `Entity.getEquipmentSnapshot()`, `EquipmentSnapshot.apply()` |
| `worldedit/WorldEdit.kt` | `WorldEdit.copy/paste/rotate/flip/fill/replace/undo/redo`, `ClipboardData` packed Long coords, per-player undo/redo stacks (max 50), Region-aware, `fillPattern()` weighted random block fill |
| `commandbuilder/CommandBuilder.kt` | `command(name) { aliases(); permission(); playerOnly(); subCommand(); onPlayerExecute {}; onExecute() }` DSL, `CommandExecutionContext(player, args, locale)`, virtual-thread execution, typed arguments, tab completion, recursive sub-commands, `RankManager` permissions |
| `commandbuilder/CommandHelpers.kt` | `OnlinePlayerCache` (5s refresh from SessionStore), `suggestPlayers(prefix)`, `resolvePlayer(name)` (online then PlayerStore) |
| `entityformation/EntityFormation.kt` | `entityFormation { circle(); line(); grid(); wedge() }` DSL, `Formation.apply(entities, center)`, `animate(speed)`, yaw rotation |
| `musicsystem/MusicSystem.kt` | `song(name) { bpm(); note(tick, instrument, pitch) }` DSL, `Instance.playSong(pos, song)`, 16 instruments, tick-based playback, `SongManager` |
| `broadcastscheduler/BroadcastScheduler.kt` | `broadcastScheduler { intervalSeconds(300); shuffled = true; message("<gold>Welcome!") }` DSL, `BroadcastScheduler` with `start()`/`stop()`/`isRunning`, cyclic message rotation to all online players |
| `motd/Motd.kt` | `motd { line1 = "<gradient:gold:yellow>Nebula"; line2 = "<gray>Play now!" }` DSL, `MotdManager` singleton with `AtomicReference<MotdConfig>`, `ServerListPingEvent` listener, MiniMessage rendering |
| `pagination/Pagination.kt` | `paginatedView<T> { items(); pageSize = 10; render { item, index -> }; header {}; footer {} }` DSL, `PaginatedView.send(player, page)`, translated header/footer, auto page clamping |

## Cosmetics System — `cosmetic/`
Player visual customization system with persistent ownership/equip state via Hazelcast stores, level progression, and GUI menus.

### Categories
| Category | Visual Effect | Utility |
|---|---|---|
| `ARMOR_SKIN` | Custom armor texture/model on player | `CustomArmorRegistry.equipFullSet()` |
| `KILL_EFFECT` | Particle burst at victim's death location | `Instance.showParticleShape {}` |
| `TRAIL` | Particle trail behind player while moving | Scheduled + `Instance.spawnParticleAt()` |
| `WIN_EFFECT` | Celebratory particles around winner | `Player.showParticleShape {}` |
| `PROJECTILE_TRAIL` | Particles along arrow flight path | Tick task + `Instance.spawnParticleAt()` |
| `COMPANION` | ModelEngine model following player | `CompanionManager` + `standAloneModel` |
| `SPAWN_EFFECT` | Particle shapes on player spawn | `Instance.showParticleShape {}` |
| `DEATH_EFFECT` | Particle shapes on player death | `Instance.showParticleShape {}` |
| `AURA` | Ambient particles around player | `AuraManager` + `Instance.spawnParticleAt()` |
| `ELIMINATION_MESSAGE` | Custom elimination chat format | `resolveEliminationMessage()` returns format key |
| `PET` | Ground-following ModelEngine pet with pathfinding | `PetManager` + `EntityCreature` + `Pathfinder` |
| `JOIN_QUIT_MESSAGE` | Custom join/quit broadcast messages | `resolveJoinMessage()` / `resolveQuitMessage()` returns format key |
| `GADGET` | Usable lobby items with cooldowns | `GadgetManager` + `Cooldown<UUID>` + `PlayerUseItemEvent` |
| `GRAVESTONE` | ModelEngine marker at death location | `GravestoneManager` + `standAloneModel` + timed despawn |
| `MOUNT` | Rideable ModelEngine entity | `CosmeticMountManager` + `MountManager` + `WalkingController` |

### Level System
- `CosmeticPlayerData.owned` is `Map<String, Int>` (cosmeticId → level, 1-based). Level 0 = not owned.
- Duplicates increment level via `UnlockCosmeticProcessor(cosmeticId, maxLevel)`. Returns `false` at cap.
- `CosmeticDefinition.maxLevel` (default 1) caps progression. `levelOverrides: Map<Int, Map<String, String>>` provides per-level data overrides.
- `resolveData(level)` merges base `data` with all applicable `levelOverrides` up to the given level (sorted ascending).
- Level-dependent outcomes: particle count/spread/radius scale with level; companions change model/scale/animation; auras evolve particle type.
- GUI shows "Level X/Y" lore when `maxLevel > 1`.

### CosmeticDefinition & CosmeticRarity
Defined in Gravity (shared source of truth). See `GRAVITY.md` for details.

### CosmeticRegistry (`CosmeticRegistry.kt`)
In-memory `object` singleton backed by `ConcurrentHashMap`:
- `register(definition)` — adds to registry
- `get(id)` / `operator []` — lookup by ID
- `byCategory(category)` — filter by category
- `all()` — all definitions
- `loadFromDefinitions()` — loads from `CosmeticDefinitions` (hardcoded in Gravity)

### CosmeticMenu (`CosmeticMenu.kt`)
GUI menus using `gui {}` and `paginatedGui {}` DSL:
- `openCategoryMenu(player)` — 5-row GUI with 15 category icons (3 rows of 5: slots 11-15, 20-24, 29-33)
- `openCosmeticList(player, category)` — paginated list, items show owned/equipped/level status, click to equip/unequip
- Level display: shows "Level X/Y" lore when `maxLevel > 1`
- Materials: LEATHER_CHESTPLATE, REDSTONE, BLAZE_POWDER, FIREWORK_ROCKET, ARROW, ARMOR_STAND, BONE, SADDLE, ENDER_PEARL, WITHER_SKELETON_SKULL, NETHER_STAR, NAME_TAG, OAK_SIGN, BLAZE_ROD, MOSSY_COBBLESTONE

### CosmeticVisibility (`CosmeticVisibility.kt`)
Per-player cosmetic display preference system:
- `CosmeticDisplayMode` enum: `FULL` (models + particles), `REDUCED` (particles only, no models), `NONE` (hide all other player cosmetics)
- `displayModeOf(playerId)` — reads `PreferenceStore.load(playerId)?.cosmeticDisplay`, returns corresponding enum
- `shouldShowModel(viewer, ownerUuid)` — returns true if viewer is the owner OR viewer preference is FULL
- `shouldShowParticles(viewer, ownerUuid)` — returns true if viewer is the owner OR viewer preference is not NONE
- Player always sees their own cosmetics regardless of preference setting
- PreferenceStore has 30s near-cache for performance in tick loops

### CosmeticApplier (`CosmeticApplier.kt`)
All particle methods are **per-player**: particles are sent individually to each eligible viewer via `CosmeticVisibility.shouldShowParticles(viewer, ownerUuid)` instead of instance-wide broadcasting. Methods accept `level: Int = 1` and `ownerUuid: UUID`:
- `applyArmorSkin(player, cosmeticId, level)` — `resolved["armorId"]` → `CustomArmorRegistry[armorId]?.equipFullSet(player)` (equipment-based, not filtered by preference)
- `clearArmorSkin(player)` — removes all armor equipment
- `playKillEffect(instance, position, cosmeticId, level, ownerUuid)` — particle shape from resolved `particle`/`shape`/`radius`/`density`
- `playWinEffect(instance, winner, cosmeticId, level)` — per-player particle shape
- `spawnTrailParticle(instance, position, cosmeticId, level, ownerUuid)` — resolved `particle`/`count`/`spread`
- `spawnProjectileTrailParticle(instance, position, cosmeticId, level, ownerUuid)` — resolved `particle`/`count`/`spread`
- `playSpawnEffect(instance, position, cosmeticId, level, ownerUuid)` — particle shape on spawn
- `playDeathEffect(instance, position, cosmeticId, level, ownerUuid)` — particle shape on death
- `spawnAuraParticles(instance, position, cosmeticId, level, ownerUuid)` — ambient particles at player+1Y
- `spawnGadgetParticle(instance, position, particle, ownerUuid, count, spread, speed)` — per-player gadget particle
- `spawnGadgetShape(instance, ownerUuid, shape)` — per-player gadget shape

### CompanionManager (`CompanionManager.kt`)
ModelEngine-based companion system:
- `ConcurrentHashMap<UUID, ActiveCompanion>` tracks active companions
- `install()` — 1-tick scheduled task for position updates (stored `Task` reference)
- `uninstall()` — cancels task, removes all companion models
- `spawn(player, cosmeticId, level)` — creates `standAloneModel` from resolved `modelId`, `scale`, `idleAnimation`
- `despawn(playerId)` — removes model
- Tick: builds `playersByUuid` map for O(1) owner lookup. Updates position to `playerPos.add(0.8, 2.0 + sin(tick * 0.1) * 0.15, 0.0)` (hover+bob), manages viewers within 48 blocks filtered by `CosmeticVisibility.shouldShowModel()`, hides viewers who changed preference to REDUCED/NONE, cleans disconnected players
- Level overrides can change `modelId`, `scale`, `idleAnimation` at higher levels

### AuraManager (`AuraManager.kt`)
Persistent ambient particle system:
- `install()` — 5-tick scheduled task (stored `Task` reference)
- `uninstall()` — cancels task
- Iterates all instance players, checks equipped AURA cosmetic via `CosmeticDataCache.get()`, resolves level, calls `CosmeticApplier.spawnAuraParticles`
- Gated by `CosmeticListener.isAllowed(AURA, auraId)`

### PetManager (`PetManager.kt`)
Ground-following pet system with A* pathfinding:
- `ConcurrentHashMap<UUID, ActivePet>` tracks active pets
- `install()` — 2-tick scheduled task for pathfinding + movement (stored `Task` reference)
- `uninstall()` — cancels task, destroys all pet models + entities
- `spawn(player, cosmeticId, level)` — creates invisible `EntityCreature` (ZOMBIE), uses non-blocking `setInstance().thenRun {}` to attach `modeledEntity()` model after instance placement. Reads `modelId`, `scale`, `walkAnimation` from resolved data.
- `despawn(playerId)` — destroys model + removes entity
- Tick: builds `playersByUuid` map for O(1) owner lookup. Calculates distance to owner. If >20 blocks, teleport. If >3 blocks, A* pathfind via `Pathfinder.findPath()` and move along path at 0.15 speed. If close, idle.
- Fallback: if pathfinding fails (no path), direct velocity toward owner.
- Animation: plays `walkAnimation` while moving, stops it when idle (falls back to auto-idle).
- Level overrides can change `modelId`, `scale`, `walkAnimation` at higher levels.
- Viewer management: shows model to nearby players within 48 blocks filtered by `CosmeticVisibility.shouldShowModel()`, hides viewers who changed preference.
- Cleanup: removes pets for disconnected players.

### GadgetManager (`GadgetManager.kt`)
Usable lobby item system with cooldowns:
- `ConcurrentHashMap<UUID, String>` tracks active gadgets (player → cosmeticId)
- `equip(player, cosmeticId, level)` — builds tagged item from cosmetic's material/name, places in hotbar slot 4
- `unequip(player)` — removes gadget item, unregisters player
- `onUse(player)` — resolves cosmetic data via `CosmeticDataCache.get()`, checks `Cooldown<UUID>` per gadget type, executes action
- Actions: `firework_launcher` (vertical velocity + per-player firework particles), `paint_blaster` (per-player particle sphere), `grappling_hook` (directional velocity from look direction)
- Data fields: `action`, `cooldownSeconds`, `particle`, `force`/`radius`/`speed` depending on action type
- Level overrides can change `cooldownSeconds`, `speed`, etc.

### GravestoneManager (`GravestoneManager.kt`)
ModelEngine marker spawned at death location:
- `ConcurrentHashMap<UUID, ActiveGravestone>` tracks active gravestones with expiry timestamps
- `install()` — 20-tick scheduled task for expiry check + viewer management (stored `Task` reference)
- `uninstall()` — cancels task, removes all gravestone models
- `spawn(instance, position, cosmeticId, level, playerUuid)` — creates `standAloneModel` from resolved `modelId`, `scale`, schedules removal after `duration` seconds. Viewers filtered by `CosmeticVisibility.shouldShowModel()`.
- Auto-despawns expired gravestones, viewer management respects preference changes
- Level overrides can change `modelId`, `scale`, `duration`

### CosmeticMountManager (`CosmeticMountManager.kt`)
Rideable ModelEngine entity system:
- `ConcurrentHashMap<UUID, ActiveMount>` tracks active mounts
- `install()` — installs `MountManager` + 1-tick scheduled task for viewer management + animation (stored `Task` reference)
- `uninstall()` — cancels task, evicts all mounted players, destroys all mount models + entities, calls `MountManager.uninstall()`
- `spawn(player, cosmeticId, level)` — creates invisible `EntityCreature` (ZOMBIE), uses non-blocking `setInstance().thenRun {}` to attach `modeledEntity()`, adds `MountBehavior` to seat bone at runtime, gives saddle item in slot 8
- `despawn(playerId)` — dismounts via `MountManager.evictPlayer()`, destroys model + entity, removes saddle item
- `toggleMount(player)` — if mounted: `MountManager.dismount()`. If not: teleports mount if >30 blocks away, then `MountManager.mount()` with `WalkingController(speed)`.
- Tick: builds `playersByUuid` map for O(1) owner lookup
- Data fields: `modelId`, `scale`, `speed`, `seatBone` (default "seat"), `walkAnimation`, `seatOffsetY`
- Animation: plays `walkAnimation` when entity velocity > threshold, stops when idle
- Level overrides can change `modelId`, `scale`, `speed`

### CosmeticDataCache (`CosmeticDataCache.kt`)
Local TTL cache wrapping `CosmeticStore.load()` to minimize Hazelcast reads in tick loops:
- `ConcurrentHashMap<UUID, CachedEntry>` with 5-second TTL
- `get(uuid)` — returns cached data if within TTL, otherwise loads from Hazelcast and caches
- `invalidate(uuid)` — removes cache entry (called on player disconnect)
- `clear()` — removes all entries (called on uninstall)
- Used by: `CosmeticListener`, `AuraManager`, `GadgetManager`

### CosmeticListener (`CosmeticListener.kt`)
Event listeners via `EventNode.all("cosmetic-listeners")`:
- **Per-mode filtering**: `@Volatile var activeConfig: CosmeticConfig` — set by `Orbit.kt` from `mode.cosmeticConfig` before `install()`. `isAllowed(category, cosmeticId)` is `internal` for AuraManager/CompanionManager access.
- **Lifecycle**: `install(handler)` registers EventNode + projectile trail task. `uninstall()` cancels task, removes EventNode, clears `CosmeticDataCache`.
- **Per-player visibility**: All particle calls pass `ownerUuid` to `CosmeticApplier`, which sends per-player filtered by `CosmeticVisibility.shouldShowParticles()`. All model managers filter viewers via `CosmeticVisibility.shouldShowModel()`.
- **Trail**: `PlayerMoveEvent` — throttled to 200ms, passes level + ownerUuid to `spawnTrailParticle`
- **Projectile trail**: 2-tick scheduled task — passes level + ownerUuid to `spawnProjectileTrailParticle`
- **Kill effect**: `onPlayerEliminated(killer, victimPosition)` — passes level
- **Death effect**: `onPlayerDeath(player, deathPosition)` — plays death effect + spawns gravestone
- **Win effect**: `onGameWon(winner)` — passes level
- **Armor skin**: `PlayerSpawnEvent` — applies armor skin with level
- **Spawn effect**: `PlayerSpawnEvent` — plays spawn effect
- **Companion**: `PlayerSpawnEvent` spawns companion, `PlayerDisconnectEvent` despawns
- **Pet**: `PlayerSpawnEvent` spawns pet, `PlayerDisconnectEvent` despawns
- **Gadget**: `PlayerSpawnEvent` equips gadget item, `PlayerUseItemEvent` (slot 4) triggers `GadgetManager.onUse()`, `PlayerDisconnectEvent` unequips
- **Mount**: `PlayerSpawnEvent` spawns mount entity + saddle item, `PlayerUseItemEvent` (slot 8) triggers `CosmeticMountManager.toggleMount()`, `PlayerDisconnectEvent` despawns
- **Gravestone**: `onPlayerDeath` spawns gravestone model at death position
- **Elimination message**: `resolveEliminationMessage(killer, victim, defaultKey)` — returns custom format key from resolved data, or default
- **Join message**: `resolveJoinMessage(player, defaultKey)` — returns custom join format key from resolved data (`joinFormatKey`), or default
- **Quit message**: `resolveQuitMessage(player, defaultKey)` — returns custom quit format key from resolved data (`quitFormatKey`), or default

### Cosmetic Catalog
Hardcoded in `CosmeticDefinitions` (Gravity). 30 cosmetics across 15 categories. See `GRAVITY.md` for full catalog.
- 27 purchasable cosmetics (COMMON=100, RARE=250, EPIC=500, LEGENDARY=1000)
- 3 achievement-exclusive (price=0): `win_effect_legend`, `kill_effect_blood`, `aura_champion`

### Integration
- Store registered in `hazelcastModule { stores { +CosmeticStore } }` in Orbit.kt
- Registry loaded after `app.start()`: `CosmeticRegistry.loadFromDefinitions()`
- Active config wired from mode: `CosmeticListener.activeConfig = mode.cosmeticConfig`
- Listener installed on global handler: `CosmeticListener.install(handler)`
- Managers installed after listener: `AuraManager.install()`, `CompanionManager.install()`, `PetManager.install()`, `GravestoneManager.install()`, `CosmeticMountManager.install()`
- **Shutdown**: all managers uninstalled in shutdown hook: `CosmeticListener.uninstall()`, `AuraManager.uninstall()`, `CompanionManager.uninstall()`, `PetManager.uninstall()`, `GravestoneManager.uninstall()`, `CosmeticMountManager.uninstall()`
- Command: `/cosmetics` opens `CosmeticMenu.openCategoryMenu(player)`
- `CosmeticConfig.enabledCategories` defaults to all 15 categories via `CosmeticCategory.entries.map { it.name }`
- `CosmeticConfig` with `enabledCategories` and `blacklist` is defined per mode: `HubDefinitions.CONFIG.cosmetics` and `Season.cosmetics` in the Season DSL. Defaults to all categories enabled with empty blacklist.

## Progression System — `progression/`

Player engagement loop: spend coins in the cosmetic shop, earn XP through battle passes, complete daily/weekly missions for rewards, achieve milestones for exclusive unlocks.

### Cosmetic Shop (in `cosmetic/CosmeticMenu.kt`)
- `CosmeticDefinition.price` — `Int`, 0 = not purchasable (achievement/BP exclusive). Prices: COMMON=100, RARE=250, EPIC=500, LEGENDARY=1000.
- Unowned + price>0 → click opens confirmation GUI (3 rows: slot 11 GREEN_WOOL confirm, slot 13 preview item, slot 15 RED_WOOL cancel)
- Confirm: `PurchaseCosmeticProcessor` on `EconomyStore` → `UnlockCosmeticProcessor` on `CosmeticStore` → reopen list. Rollback via `AddBalanceProcessor` if unlock fails.
- Leveled cosmetics: upgrade cost = `price * (currentLevel + 1)`

### Battle Pass — `BattlePassRegistry.kt`, `BattlePassManager.kt`, `BattlePassMenu.kt`
- `BattlePassRegistry` — thin wrapper delegating to `BattlePassDefinitions` from Gravity (hardcoded source of truth)
- `BattlePassManager` — stateless orchestrator:
  - `addXp(player, passId, amount)` — single-pass XP addition via `AddBattlePassXpProcessor`
  - `addXpToAll(player, amount)` — single `AddXpToAllPassesProcessor` call processes all active passes atomically
  - `claimReward(player, passId, tier, premium)` — claim processor + grant (coins via `AddBalanceProcessor`, cosmetic via `UnlockCosmeticProcessor`)
  - `purchasePremium(player, passId)` — atomic coins deduction via `PurchaseCosmeticProcessor` + `SetBattlePassPremiumProcessor`, rollback on failure
- XP sources: participation=50, per kill=10, win=100, mission completion=per-mission
- `BattlePassMenu` — two-level GUI:
  - Pass selector (3 rows): one item per active pass, shows tier + XP progress. Skips to tier view if only 1 active pass.
  - Tier view (6 rows, paginated): 7 tiers/page (slots 10-16). Row 2=free rewards (19-25), row 3=XP progress bar, row 4=premium rewards (37-43). Nav arrows slots 45/53, back button slot 49.
  - Tier items: LIME_STAINED_GLASS_PANE (claimable), GREEN_CONCRETE (claimed), RED_STAINED_GLASS_PANE (locked), PURPLE_STAINED_GLASS_PANE (premium locked)
  - Premium purchase button (slot 47, GOLD_INGOT) shown when player hasn't unlocked premium. Opens confirmation GUI (3 rows: confirm/preview/cancel).

### Missions — `mission/MissionRegistry.kt`, `mission/MissionTracker.kt`, `mission/MissionMenu.kt`
- `MissionRegistry` — thin wrapper delegating to `MissionTemplates` from Gravity (hardcoded source of truth, 8 daily + 6 weekly pool)
- `MissionTracker` — hooks into game events: `onKill`, `onWin`, `onGamePlayed`, `onSurvivalMinute`, `onDamageDealt`, `onTopPlacement`, `onUseCategory`. Each calls `IncrementMissionProcessor` → on completion grants BP XP + coins + increments `mission_master` achievement + translated notification.
- `MissionMenu` — 4-row GUI. Slot 4 CLOCK "Daily", slots 10/12/14 daily items. Slot 22 COMPASS "Weekly", slots 19/21/23 weekly items. Color-coded: LIME_DYE (completed, glowing), YELLOW_DYE (in progress + bar), GRAY_DYE (not started). Lore: reward preview + reset timer.
- First-login: `AsyncPlayerConfigurationEvent` assigns random daily+weekly if `MissionStore.load(uuid) == null`
- Rotation: handled by Pulsar's `MissionRotationRoutine` using `dailyExpiredPredicate`/`weeklyExpiredPredicate` from Gravity

### Achievements — `achievement/AchievementContent.kt`, `achievement/AchievementMenu.kt`
- `registerAchievementContent()` registers 21 achievements across 6 categories using existing `achievement {}` DSL
- Categories: GENERAL, COMBAT, SURVIVAL, SOCIAL, EXPLORATION, MASTERY
- Stat-based thresholds via `AchievementTriggerManager.bindThreshold()` — single `StatStore.load(uuid)` → in-memory check → batch unlock
- Kill streak triggers: `double_trouble` (2), `unstoppable` (5), `rampage` (10)
- Achievement-exclusive cosmetics (price=0, unlocked via `onUnlock`): `legend→win_effect_legend`, `mass_murderer→kill_effect_blood`, `bp_complete→aura_champion`
- `AchievementMenu` — 4-row category selector (slots 10-15) → paginated list per category. Completed=LIME_DYE glowing, progress=icon+bar, hidden=COAL_BLOCK "???"

### BattleRoyaleMode Integration
- `onPlayerDamaged`: `MissionTracker.onKill(killer)` + `BattlePassManager.addXpToAll(killer, 10)` + kill streak achievement triggers
- `persistGameStats`: `MissionTracker.onGamePlayed/onWin` + `BattlePassManager.addXpToAll` (50 participation, 100 win) + `invincible` achievement check + `AchievementTriggerManager.evaluate`

### Hub Integration
- Hotbar items: slot 0 EMERALD "Cosmetics", slot 2 EXPERIENCE_BOTTLE "Battle Pass", slot 4 COMPASS "Server Selector", slot 6 PAPER "Missions", slot 8 DIAMOND "Achievements"
- Action mappings: `open_cosmetics`, `open_battlepass`, `open_missions`, `open_achievements`

### Commands
- `/battlepass` — opens `BattlePassMenu.open(player)`
- `/missions` — opens `MissionMenu.open(player)`
- `/achievements` — opens `AchievementMenu.open(player)`

### Stores
- `BattlePassStore`, `MissionStore` registered in `hazelcastModule` stores block
- `AchievementRegistry.loadPlayer/unloadPlayer` in config/disconnect events

## Translation System — `translation/`
- `OrbitTranslations.register(translations)` registers all keys for mechanics and utilities in `en` locale. Hub text is config-driven (not translations).
- `TranslationExtensions.kt` provides:
  - `Player.translate(key, vararg args)` — locale-aware translation returning `Component`
  - `Player.translateRaw(key, vararg args)` — locale-aware raw string translation
  - `translateDefault(key, vararg args)` — default-locale translation (no player context)
  - `translateFor(uuid, key, vararg args)` — UUID-based locale-aware translation
- All utility player-facing strings route through the translation system:
  - `achievement/Achievement.kt` — unlock notification title (`orbit.util.achievement.unlocked`)
  - `compass/CompassTracker.kt` — action bar display (`orbit.util.compass.display`)
  - `instancepool/InstancePool.kt` — kick message on release (`orbit.util.instance_pool.released`)
  - `stattracker/StatTracker.kt` — rank entry rendering (`orbit.util.leaderboard.entry`)
  - `matchresult/MatchResult.kt` — draw/winner/mvp/stats/duration labels (`orbit.util.match_result.*`)
  - `pagination/Pagination.kt` — page header and navigation footer (`orbit.util.pagination.*`)
  - `timer/Timer.kt` — default timer display (`orbit.util.timer.display`)
  - `world/WorldManager.kt` — kick message on delete (`orbit.util.world.deleted`)
  - `anvilloader/AnvilWorldLoader.kt` — kick message on unload (`orbit.util.world.unloading`)
- All mechanic player-facing strings route through the translation system:
  - `anvil/AnvilModule.kt` — inventory title (`orbit.mechanic.anvil.title`)
  - `armortrim/ArmorTrimModule.kt` — inventory title (`orbit.mechanic.armor_trim.title`)
  - `barrel/BarrelModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.barrel.title`)
  - `blastfurnace/BlastFurnaceModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.blast_furnace.title`)
  - `brewingstand/BrewingStandModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.brewing_stand.title`)
  - `bundle/BundleModule.kt` — inventory title (`orbit.mechanic.bundle.title`)
  - `cartographytable/CartographyTableModule.kt` — inventory title (`orbit.mechanic.cartography_table.title`)
  - `commandblock/CommandBlockModule.kt` — info message with `<command>` placeholder (`orbit.mechanic.command_block.info`)
  - `crafter/CrafterModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.crafter.title`)
  - `crafting/CraftingModule.kt` — inventory title (`orbit.mechanic.crafting.title`)
  - `doublechest/DoubleChestModule.kt` — large/single chest titles via `translateDefault` (`orbit.mechanic.double_chest.title`, `orbit.mechanic.chest.title`)
  - `dropper/DropperModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.dropper.title`)
  - `enchanting/EnchantingModule.kt` — inventory title (`orbit.mechanic.enchanting.title`)
  - `enderchest/EnderChestModule.kt` — per-player inventory title (`orbit.mechanic.ender_chest.title`)
  - `fletchingtable/FletchingTableModule.kt` — inventory title (`orbit.mechanic.fletching_table.title`)
  - `grindstone/GrindstoneModule.kt` — inventory title (`orbit.mechanic.grindstone.title`)
  - `headdrop/HeadDropModule.kt` — skull name/lore with `<victim>`/`<killer>` placeholders (`orbit.mechanic.head_drop.name`, `orbit.mechanic.head_drop.lore`)
  - `hopper/HopperModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.hopper.title`)
  - `itemrepair/ItemRepairModule.kt` — inventory title (`orbit.mechanic.item_repair.title`)
  - `lectern/LecternModule.kt` — book header and empty messages (`orbit.mechanic.lectern.book_header`, `orbit.mechanic.lectern.empty`)
  - `loom/LoomModule.kt` — inventory title (`orbit.mechanic.loom.title`)
  - `map/MapModule.kt` — inventory title (`orbit.mechanic.map.title`)
  - `shulkerbox/ShulkerBoxModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.shulker_box.title`)
  - `sign/SignModule.kt` — sign header and line messages with `<number>`/`<text>` placeholders (`orbit.mechanic.sign.header`, `orbit.mechanic.sign.line`)
  - `smithingtable/SmithingTableModule.kt` — inventory title (`orbit.mechanic.smithing_table.title`)
  - `smoker/SmokerModule.kt` — inventory title via `translateDefault` (`orbit.mechanic.smoker.title`)
  - `stonecutter/StonecutterModule.kt` — inventory title (`orbit.mechanic.stonecutter.title`)
  - `trading/TradingModule.kt` — inventory title (`orbit.mechanic.trading.title`)
  - `recoverycompass/RecoveryCompassModule.kt` — action bar with `<direction>`/`<distance>` placeholders (`orbit.mechanic.recovery_compass.distance`)

## Admin Commands

### `/me` — ModelEngine Command (`utils/modelengine/ModelEngineCommand.kt`)
Permission: `orbit.modelengine`

| Sub-command | Args | Action |
|---|---|---|
| `list` | — | List all registered blueprints with bone count + animation count |
| `info <blueprint>` | `blueprint: Word` | Show blueprint details: bones (tree), animations (name, duration, loop mode), root bones |
| `spawn <blueprint> [scale]` | `blueprint: Word`, `scale: Float (default 1.0)` | Spawn `standAloneModel` at player position, show to player |
| `despawn` | — | Remove all standalone models spawned via `/me spawn` |
| `reload <name>` | `name: Word` | Re-parse `data/models/<name>.bbmodel` via `ModelGenerator.generateRaw`, re-register blueprint |
| `entities` | — | List all tracked `ModeledEntity` instances with position, model names, viewer count |
| `ids` | — | Show ModelIdRegistry stats: total assigned IDs, sample entries |

- Tab completion for `<blueprint>` from `ModelEngine.blueprints().keys`
- Tab completion for `<name>` from `resources.list("models", "bbmodel")`
- Spawned models tracked in file-level `ConcurrentHashMap.newKeySet<StandaloneModelOwner>()` for `despawn`
- Registered in `Orbit.kt` after `CustomContentRegistry.init()`

### `/cc` — CustomContent Command (`utils/customcontent/CustomContentCommand.kt`)
Permission: `orbit.customcontent`

| Sub-command | Args | Action |
|---|---|---|
| `items` | — | List all custom items: id, base material, CMD ID |
| `blocks` | — | List all custom blocks: id, hitbox type, allocated state, CMD ID |
| `give <item> [amount]` | `item: Word`, `amount: Int (default 1)` | Give `CustomItem.createStack(amount)` to player |
| `info <id>` | `id: Word` | Show full details of item or block (checks both registries) |
| `pack` | — | Trigger `CustomContentRegistry.mergePack()`, report size + SHA-1 |
| `allocations` | — | Show per-hitbox pool usage: used/total for each `BlockHitbox` type |

- Tab completion for `<item>` from `CustomItemRegistry.all().map { it.id }`
- Tab completion for `<id>` from combined item + block IDs
- Registered in `Orbit.kt` after `CustomContentRegistry.init()`. Pack sending removed — delegated to proxy.

### Basic Commands (`commands/BasicCommands.kt`)
Installed via `installBasicCommands(commandManager)` — registers all commands + god mode listeners in `EventNode.all("basic-commands")`. `uninstallBasicCommands()` removes EventNode and clears god set.

| Command | Aliases | Permission | Args | Action |
|---|---|---|---|---|
| `/gamemode` | `/gm` | `orbit.command.gamemode` | `<mode> [player]` | Set gamemode (survival/s/0, creative/c/1, adventure/a/2, spectator/sp/3) |
| `/fly` | — | `orbit.command.fly` | `[player]` | Toggle flight |
| `/heal` | — | `orbit.command.heal` | `[player]` | Restore to max health |
| `/feed` | — | `orbit.command.feed` | `[player]` | Restore food (20) + saturation (5.0) |
| `/tp` | `/teleport` | `orbit.command.teleport` | `<player>` or `<x> <y> <z>` | Teleport to player or coordinates |
| `/speed` | — | `orbit.command.speed` | `<1-10> [player]` | Set walk (0.1×n) + fly (0.05×n) speed |
| `/kill` | — | `orbit.command.kill` | `[player]` | Kill player |
| `/clear` | `/clearinventory`, `/ci` | `orbit.command.clear` | `[player]` | Clear inventory |
| `/ping` | — | `orbit.command.ping` | `[player]` | Show latency in ms |
| `/god` | — | `orbit.command.god` | `[player]` | Toggle invulnerability (cancels EntityDamageEvent) |
| `/give` | — | `orbit.command.give` | `<item> [amount] [player]` | Give items (material by namespace key, amount 1-6400) |
| `/time` | — | `orbit.command.time` | `<value>` | Set time (day/noon/night/midnight or raw ticks) |
| `/weather` | — | `orbit.command.weather` | `<type>` | Set weather (clear/rain/thunder) via `WeatherController` |
| `/tphere` | `/s2l` | `orbit.command.tphere` | `<player>` | Teleport player to you |
| `/invsee` | — | `orbit.command.invsee` | `<player>` | View player's inventory (read-only GUI, 5 rows) |

All commands: permission-gated via `RankManager`, tab-complete player names via `OnlinePlayerCache`, run on virtual threads.

### Game Commands (`commands/GameCommands.kt`)
Installed via `installGameCommands(commandManager)` — game engine admin commands. Only functional when `Orbit.mode` is a `GameMode` instance; returns error otherwise.

| Command | Permission | Args | Action |
|---|---|---|---|
| `/eliminate` | `orbit.command.eliminate` | `<player>` | Eliminate player (PLAYING phase, must be alive) |
| `/revive` | `orbit.command.revive` | `<player>` | Revive spectating player (PLAYING phase, sets SURVIVAL + teleport to spawn) |
| `/reconnect` | `orbit.command.reconnect` | `<player>` | Force reconnect a player in disconnected state (cancels elimination timer, clears ReconnectionStore, triggers `onPlayerReconnected`) |
| `/forcestart` | `orbit.command.forcestart` | — | Force start game from WAITING/STARTING (skips countdown, transitions directly to PLAYING) |
| `/forceend` | `orbit.command.forceend` | — | Force end game (PLAYING phase, triggers draw result) |
| `/phase` | `orbit.command.phase` | — | Show current game phase |
| `/alive` | `orbit.command.alive` | — | Show alive/disconnected/spectating counts and player names |

All commands validate phase and player state before execution. `/reconnect` requires the target player to be online on the server AND in `Disconnected` state in the tracker.
