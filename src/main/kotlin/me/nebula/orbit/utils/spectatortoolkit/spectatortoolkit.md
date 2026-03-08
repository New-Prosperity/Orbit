# SpectatorToolkit

Hotbar-based spectator navigation for eliminated players. Provides player cycling, speed control, player selector GUI, and optional leave button.

## API

### DSL

```kotlin
val toolkit = spectatorToolkit {
    onNext { player -> gameMode.nextSpectatorTarget(player) }
    onPrevious { player -> gameMode.previousSpectatorTarget(player) }
    alivePlayers { tracker.alive.mapNotNull { getOnlinePlayerByUuid(it) } }
    speedSteps(1f, 2f, 4f)
    onLeave { player -> player.kick(Component.text("Left")) }
}
toolkit.install()
toolkit.apply(player)
toolkit.remove(player)
toolkit.uninstall()
```

### Hotbar Layout

| Slot | Item | Action |
|------|------|--------|
| 0 | Compass | Next spectator target |
| 1 | Clock | Previous spectator target |
| 4 | Paper | Open player selector GUI |
| 7 | Feather | Cycle fly speed (1x → 2x → 4x) |
| 8 | Barrier | Leave (optional) |

### Player Selector

Opens a chest GUI listing all alive players with player heads. Clicking a player starts spectating them.

### Speed Control

Cycles through configured speed multipliers (default: 1x, 2x, 4x). Modifies `flyingSpeed` relative to the vanilla default (0.05).

### Integration

`GameMode` calls `buildSpectatorToolkit()` during `enterPlaying()`. Override in your mode:

```kotlin
override fun buildSpectatorToolkit() = spectatorToolkit {
    onNext { player -> nextSpectatorTarget(player) }
    onPrevious { player -> previousSpectatorTarget(player) }
    alivePlayers { tracker.alive.mapNotNull { getPlayer(it) } }
}
```

The toolkit is automatically applied to eliminated players and removed on revive.
