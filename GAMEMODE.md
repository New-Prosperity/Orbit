# GameMode

Contract for `Orbit/src/main/kotlin/me/nebula/orbit/mode/game/GameMode.kt`. Read this before authoring a new mode.

`GameMode` is an orchestrator: it owns the phase FSM, wires ~20 helper subsystems, and dispatches to open/abstract hooks you override. You write the *rules of your game*; the base class handles queue-to-podium plumbing.

## Minimum viable mode

```kotlin
private val IT_TAG = Tag.Boolean("tag:it")

class TagMode : GameMode() {
    override val settings: GameSettings = GameSettings(
        worldPath = "maps/arena.nebula",
        preloadRadius = 4,
        spawn = SpawnConfig(/* ... */),
        scoreboard = /* ... */,
        tabList = /* ... */,
        lobby = /* ... */,
        hotbar = emptyList(),
        timing = TimingConfig(
            minPlayers = 2, maxPlayers = 16,
            countdownSeconds = 10, gameDurationSeconds = 180,
            endingDurationSeconds = 15, reconnectWindowSeconds = 30,
        ),
    )

    override fun buildPlaceholderResolver() = placeholderResolver {
        global("mode") { "tag" }
    }

    override fun onGameSetup(players: List<Player>) {
        players.random().setTag(IT_TAG, true)
    }

    override fun checkWinCondition(): MatchResult? {
        val survivors = tracker.alive.mapNotNull {
            MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(it)
        }.filter { it.getTag(IT_TAG) != true }
        return if (survivors.size == 1) matchResult { winner(survivors.first()) } else null
    }
}
```

Registered in `Orbit.resolveMode()`. For a lighter-weight alternative without subclassing, see the `gameMode(name) { }` DSL in `GameModeDsl.kt` — same hooks, builder form. For `SpawnConfig` / `ScoreboardConfig` / `LobbyConfig` shapes, see `SeasonConfig` in `BattleRoyaleMode` for authoritative defaults.

Everything below is optional.

## What you must provide

| Symbol | Purpose |
| --- | --- |
| `val settings: GameSettings` | Timing, spawn, scoreboard, tablist, lobby, teams, overtime, late-join config |
| `buildPlaceholderResolver()` | Placeholders for scoreboard/tablist (`{online}`, `{alive}`, `{mode}`, etc.) |
| `onGameSetup(players)` | Called once when PLAYING starts. Hand out items, teleport to spawns, install mode-specific state |
| `checkWinCondition()` | Return a `MatchResult` to end the game, or `null` to keep playing. Called every tick during PLAYING and on every eliminate |

## What you get for free

Subsystems are installed automatically — no registration needed:

**Lifecycle & state**
- Phase FSM: `WAITING` → `STARTING` → `PLAYING` → `ENDING` → `WAITING`
- `PlayerTracker` — alive / dead / spectating / disconnected / teams / tags
- `GameRules` with typed keys + change events via `events`
- `GameEventBus` (`events`) — subscribe to `PhaseChanged`, `RuleChanged`, and mode-specific events
- `GameStateMachine` transitions validated; illegal transitions throw
- `VariantController` — variant pool with random selection per game (override `variantPool()`)

**Player lifecycle**
- First-spawn, disconnect, reconnect routing (`PlayerLifecycleHandler`)
- `ReconnectionManager` — disconnect window + auto-eliminate on timeout
- `RespawnManager` — respawn positions, death handling, kit re-apply
- `LateJoinManager` — mid-game joins up to `settings.lateJoin.maxLateJoiners`
- `ActivityWatchdog` — AFK detection, void-floor kick, gameplay loops
- `GracePeriodManager` — spawn immunity
- `SpectatorManager` — cycle targets, auto-spectate on eliminate

**Combat**
- `EliminationHandler` — `eliminate(player)` / `revive(player)` / `handleDeath(player, killer)` with kill-streak counting
- `DamageRouter` — friendly-fire check via `settings.teams.friendlyFire`, dispatches to `onPlayerDamaged`

**End-game**
- `OvertimeController` — auto-starts on timer expiry if `settings.overtime` is set
- `MatchResultDisplay` — end-of-match title broadcast
- `RatingManager` — rating delta math (skipped for hosted games)
- `ReplayFlusher` — ships `PacketReplayRecorder` + `ReplayRecorder` streams at ENDING
- Auto `games_played` + `wins` achievement progress
- `ProgressionEvent.TopPlacement` + `ProgressionEvent.KillStreak` published automatically

**UI**
- `LiveScoreboard` + `LiveTabList` from `settings.scoreboard` / `settings.tabList`
- `RuleUiWatcher` — pushes rule changes to clients
- `LobbyLifecycleManager` — lobby instance, hotbar, waiting action bar

**World**
- Dual-instance support: `lobbyInstance` for WAITING/STARTING, `gameInstance` for PLAYING/ENDING
- Auto-loads from `.nebula` via `NebulaWorldLoader`
- `MutatorEngine` application per game
- `invalidateGameInstance()` for per-round world regen

## Optional hooks

All `open fun` with empty or no-op defaults. Override what you need; ignore the rest.

**Phase callbacks** — `onWaitingStart`, `onPlayingStart`, `onEndingStart(result)`, `onEndingComplete`, `onGameReset`, `onCountdownTick(remaining)`

**Player events** — `onPlayerJoinWaiting`, `onPlayerLeaveWaiting`, `onPlayerDeath(player, killer)`, `onPlayerEliminated`, `onPlayerDisconnected`, `onPlayerReconnected`, `onPlayerRespawn`, `onLateJoin`, `onAfkEliminated`, `onCombatLog`

**Game flow** — `onKillStreak(player, streak)`, `onOvertimeStart`, `onOvertimeEnd`, `onAllPlayersEliminated` (default: draw), `onPlayerDamaged` (return false to veto), `onTeamsAssigned`

**Builders** — return `null` to disable:
- `buildLobbyHotbar()` — `Hotbar` installed during WAITING
- `buildRespawnKit()` — `Kit` applied on every respawn
- `buildRespawnPosition(player)` — defaults to `spawnPoint`
- `buildChatPipeline()` — game chat scoping (team-only, spectator, etc.)
- `buildSpectatorToolkit()` — spectator compass, teleport menu, etc.
- `buildKillFeed()` — killfeed display
- `buildDeathRecapTracker()` — defaults to a blank tracker (damage audit)
- `buildRewardDistributor()` — end-of-game coin/XP distribution
- `buildCeremony(result)` — winner podium + fireworks
- `buildComboCounter()` — hit-combo HUD

**Timing** — `resolveGameDuration()`, `buildTimeExpiredResult()`, `buildOvertimeResult()`

**Teams** — `assignTeams(players)` — default uses `settings.teams.teamCount` round-robin; `autoBalanceTeams(names)` helper uses `TeamBalance` for skill-weighted split

**Stats** — `persistGameStats(result)` — called during ENDING, before rating + rewards. Write to your own stores here

**Variants** — `variantPool()` — return a `GameVariantPool` to enable variant selection

**Instance** — `createGameInstance()` — override if you need custom world generation (see `BattleRoyaleMode.createGameInstance`)

## Phase lifecycle

```
WAITING (lobby world, lobby hotbar active)
  ↓ minPlayers reached or host forceStart
STARTING (freeze event node installed, countdown sound every tick 1-3)
  ↓ countdown complete
PLAYING (lobby torn down, game instance activated, mechanics node installed,
         variant selected, mutators applied, onGameSetup called,
         grace period applied, game timer started)
  ↓ checkWinCondition != null OR timer expires OR forceEnd
ENDING (gameplay timers stopped, placements frozen, result stored,
        persistGameStats → rating → replay flush → rewards →
        MatchResultDisplay + ceremony, endingCountdown runs)
  ↓ endingCountdown complete
(server terminates — not WAITING — in current single-game model)
```

Phase transitions publish `GameEvent.PhaseChanged(from, to)` on `events`. Subscribe via `onPhaseChange { from, to -> }` or `events.subscribe<GameEvent.PhaseChanged> {}`.

## Rules of engagement

- **Never reach inside helpers directly.** Use the public API. Helpers are `internal` or `private` for a reason — they coordinate with each other, and skipping the orchestrator breaks invariants
- **Never touch `phase` to force transitions.** Use `forceStart()`, `forceEnd(result)`, or let the state machine progress naturally
- **`onGameSetup` runs on the game instance**, not the lobby. Players are already teleported when you get the callback
- **`checkWinCondition()` runs hot** — called on every eliminate + every timer tick. Keep it cheap; cache if you compute from snapshots
- **Match the win condition to the mode.** Default `onAllPlayersEliminated` is a draw — fine for BR, wrong for round-based or point-based modes. Override explicitly
- **Register event subscriptions in `onPlayingStart`**, cancel in `onEndingStart` or `onGameReset`. The base class doesn't clean mode-subscribed listeners for you
- **Publish your own `GameEvent` subtypes** — define a sealed subclass, publish via `events.publish(...)`, subscribers get it over the same bus

## Testing

- Unit-test helpers (`PlayerTracker`, `OvertimeController`, etc.) directly — they have no `GameMode` dependency
- Integration-test a mode by subclassing, driving `forceStart()`/`forceEnd()`, and asserting on `tracker` + `placementOf(uuid)` + `MatchResultManager.latest`
- Full E2E goes through `NebulaTestFixture` — spawns real bots, drives the FSM end-to-end. See `BattleRoyaleScriptTest` for the pattern

## Reference implementation

`Orbit/src/main/kotlin/me/nebula/orbit/mode/game/battleroyale/BattleRoyaleMode.kt` (~870 lines) is the exemplar. It exercises every hook, registers per-game subsystems (zone FSM, downed/revive, kill pipeline, legendary weapon drops, vote manager, supply drops), implements all four builder methods, uses variants + mutators, customizes the instance via `BattleRoyaleMapGenerator`. When in doubt, grep there for how a thing is done.
