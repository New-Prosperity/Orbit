# SpectatorToolkit

Hotbar + shader-HUD spectator overlay for eliminated players. Provides player cycling, free camera, click-to-spectate selector, fly-speed control, watched-target stats, alive count, game timer, mutual spectator hiding, and an optional leave button.

## API

### DSL

```kotlin
val toolkit = spectatorToolkit {
    onNext { player -> mode.nextSpectatorTarget(player) }
    onPrevious { player -> mode.previousSpectatorTarget(player) }
    alivePlayers { tracker.alive.mapNotNull(::getPlayer) }
    speedSteps(1f, 2f, 4f)
    onLeave { player -> player.kick(Component.text("Left")) }

    hud()                                    // enable shader HUD overlay
    freeCamera()                             // enable free-cam toggle
    hideOtherSpectators()                    // make spectators invisible to each other
    aliveCount { tracker.aliveCount }
    gameTimer { formatRemainingTime() }      // returns "M:SS"
    targetStats { target ->
        SpectatorTargetStats(
            kills = StatTracker.get(target, "kills").toInt(),
            team = tracker.teamOf(target.uuid),
        )
    }
    maxHealthSegments(10)                    // HUD bar segment count (default 10)
    maxArmorSegments(10)
    tickInterval(10)                         // HUD update interval, ticks
}
toolkit.install()
toolkit.apply(player)
toolkit.remove(player)
toolkit.uninstall()
```

### Hotbar Layout

| Slot | Item       | Action                                       |
|------|------------|----------------------------------------------|
| 0    | COMPASS    | Next spectator target                        |
| 1    | CLOCK      | Previous spectator target                    |
| 3    | ENDER_EYE  | Toggle free camera (only if `freeCamera()`)  |
| 4    | PLAYER_HEAD| Open enriched player selector GUI            |
| 7    | FEATHER    | Cycle fly speed                              |
| 8    | BARRIER    | Leave (only if `onLeave` is set)             |

Toggling free camera while a target is set stashes the last target and detaches via `stopSpectating()`. Toggling off restores the last target via `spectate()`. Pressing Next/Previous while in free-cam exits free-cam first.

### HUD Layout (`spectator-hud`)

Shader-driven via `HudManager`. Built lazily on first `install()` (idempotent). Elements:

| Element              | Anchor         | Description                                              |
|----------------------|----------------|----------------------------------------------------------|
| `timer`              | TOP_CENTER     | Game timer text "M:SS".                                  |
| `alive`              | TOP_RIGHT      | Alive player count.                                      |
| `target_name`        | BOTTOM_CENTER  | Watched target's username (capped at 12 chars).          |
| `target_health`      | BOTTOM_CENTER  | 10-segment red bar showing watched target's health.      |
| `target_armor`       | BOTTOM_CENTER  | 10-segment blue bar showing watched target's armor.     |
| `target_kills`       | BOTTOM_CENTER  | Watched target's kill count.                             |
| `freecam_indicator`  | TOP_LEFT       | Sprite shown only when free-cam mode is active.          |
| `killfeed`           | TOP_LEFT       | Most recent kill as `KILLER>VICTIM`, auto-fades after 4s.|

The `target_*` elements are conditionally hidden when no current target or in free-cam (`setHudCondition`). The `killfeed` element uses a time-based condition that returns true only while `now - lastKillTimestamp < KILL_FEED_TTL_MS` (4 seconds).

The HUD `text` element now supports A-Z, a-z, 0-9, `:`, `/`, `.`, `%`, `-`, `_`, `>`, spaces, and inline `{sprite_id}` references thanks to the registered letter font (see `utils/hud/hud.md`).

A periodic task (`tickInterval`, default 10 ticks) refreshes the HUD values for every player tagged `spectator:active`.

### Kill Feed Integration

`recordKill(killerName: String?, victimName: String)` is the public method game modes call to push a kill into the spectator HUD. Names are truncated to 12 chars and joined as `"$killer>$victim"` (or `"->$victim"` for environmental deaths). The toolkit stores `lastKillText` + `lastKillTimestamp` and the next HUD tick renders it; the time-based condition hides the element after the TTL elapses.

In `BattleRoyaleMode.buildKillFeed`, this is wired via a `KillFeedEffect`:

```kotlin
effect { event, _ ->
    mode.spectatorToolkit?.recordKill(event.killer?.username, event.victim.username)
}
```

`spectatorToolkit` is exposed on `GameMode` as a `var` with `private set` so subclasses can read it for wiring.

### Player Selector

Opens a chest GUI sized to candidate count, sorted by distance. Each head shows:
- Name
- Health hearts (0-10 ❤)
- Armor (0-10 ⛨)
- Kills / team / kit (from `targetStats` provider, optional)
- Distance to spectator
- "click to watch" hint

Vanish-aware via `VanishManager.canSee`. Self-filtered. Empty list → action bar `orbit.spectator.no_targets`.

### Free Camera

`Tag.Boolean("spectator:freecam")` is set on the player while active. The toolkit stashes the previous target in `Tag.UUID("spectator:freecam_last_target")` so toggling off can resume spectating the same player. Entering teleports the player to the last watched position.

### Hide Other Spectators

When `hideOtherSpectators()` is enabled, the toolkit maintains a `Set<UUID>` of active spectators and applies a `updateViewableRule { viewer.uuid !in spectators }` rule on each spectator entity. The set is updated on `apply` / `remove` / disconnect, and the rule is reread by Minestom's view engine on the next visibility check.

A self-owned `EventNode` cleans up the set on `PlayerDisconnectEvent` so a spectator who disconnects mid-game is removed from the rule predicate.

### Speed Control

Cycles through configured speed multipliers (default 1x → 2x → 4x). Modifies `flyingSpeed` relative to vanilla 0.05. Speed index stored on the player via `Tag.Integer("spectator:speed_index")` so it auto-cleans on entity removal.

### Tags

| Tag                                  | Type    | Purpose                                       |
|--------------------------------------|---------|-----------------------------------------------|
| `spectator:active`                   | Boolean | Player currently has the toolkit applied      |
| `spectator:speed_index`              | Int     | Index into `speedSteps`                       |
| `spectator:current_target`           | UUID    | Currently watched player                      |
| `spectator:freecam`                  | Boolean | Free-cam mode active                          |
| `spectator:freecam_last_target`      | UUID    | Stashed target while in free-cam              |

### Integration

`GameMode` calls `buildSpectatorToolkit()` during `enterPlaying()`, applies on `eliminate(player)`, removes on `revive(player)`. Override per game mode:

```kotlin
override fun buildSpectatorToolkit() = spectatorToolkit {
    onNext { mode.nextSpectatorTarget(it) }
    onPrevious { mode.previousSpectatorTarget(it) }
    alivePlayers { tracker.alive.mapNotNull(::getPlayer) }
    hud()
    freeCamera()
    hideOtherSpectators()
    aliveCount { tracker.aliveCount }
    gameTimer { formatRemainingTime() }
    targetStats { SpectatorTargetStats(kills = StatTracker.get(it, "kills").toInt()) }
}
```

## Translation Keys

| Key                                       | Placeholders          |
|-------------------------------------------|-----------------------|
| `orbit.spectator.watching`                | `{name}`              |
| `orbit.spectator.speed`                   | `{speed}`             |
| `orbit.spectator.no_targets`              | —                     |
| `orbit.spectator.freecam.enabled`         | —                     |
| `orbit.spectator.freecam.disabled`        | —                     |
| `orbit.spectator.selector_title`          | —                     |
| `orbit.spectator.lore.name`               | `{name}`              |
| `orbit.spectator.lore.health`             | `{bar}`               |
| `orbit.spectator.lore.armor`              | `{bar}`               |
| `orbit.spectator.lore.kills`              | `{kills}`             |
| `orbit.spectator.lore.team`               | `{team}`              |
| `orbit.spectator.lore.kit`                | `{kit}`               |
| `orbit.spectator.lore.distance`           | `{distance}`          |
| `orbit.spectator.lore.click_to_watch`     | —                     |

Translations live in the `translations` Hazelcast replicated map (populated externally). The toolkit only references keys.
