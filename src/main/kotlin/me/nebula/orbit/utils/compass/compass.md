# CompassTracker

Tracks a target (player or position) for players holding a compass, displaying direction and distance on the action bar.

## API

| Method | Description |
|---|---|
| `CompassTracker.track(player, target)` | Track another player |
| `CompassTracker.track(player, position)` | Track a fixed position |
| `CompassTracker.untrack(player)` | Stop tracking |
| `CompassTracker.getTarget(player)` | Get current target |
| `CompassTracker.isTracking(player)` | Check if player is tracking |
| `CompassTracker.start(updateIntervalTicks)` | Start the tick loop (default 10 ticks) |
| `CompassTracker.stop()` | Stop the tick loop |
| `CompassTracker.clear()` | Remove all targets and stop |

## Extension Functions

| Function | Description |
|---|---|
| `player.trackPlayer(target)` | Shortcut for `CompassTracker.track` |
| `player.trackPosition(pos)` | Shortcut for `CompassTracker.track` |
| `player.untrackCompass()` | Shortcut for `CompassTracker.untrack` |

## Display

Players holding a compass see an action bar message with cardinal direction (N/NE/E/SE/S/SW/W/NW) and distance in meters. Shows "HERE" when within 5 blocks.

## Example

```kotlin
CompassTracker.start(updateIntervalTicks = 10)

player.trackPlayer(targetPlayer)

player.trackPosition(Pos(100.0, 64.0, 200.0))

player.untrackCompass()
```
