# Trail

Particle trail system that renders particles behind moving players, visible to nearby viewers.

## TrailConfig

```kotlin
TrailConfig(
    particle = Particle.FLAME,
    count = 1,
    offsetX = 0f,
    offsetY = 0f,
    offsetZ = 0f,
    speed = 0f,
    heightOffset = 0.1,
    visibilityRadius = 32.0,
)
```

## API

| Method | Description |
|---|---|
| `TrailManager.setTrail(player, config)` | Assign a trail to a player |
| `TrailManager.removeTrail(player)` | Remove a player's trail |
| `TrailManager.hasTrail(player)` | Check if player has a trail |
| `TrailManager.getTrail(player)` | Get trail config |
| `TrailManager.start(intervalTicks)` | Start the particle tick loop (default 2 ticks) |
| `TrailManager.stop()` | Stop the tick loop |
| `TrailManager.clear()` | Remove all trails and stop |

## Extension Functions

| Function | Description |
|---|---|
| `player.setTrail(config)` | Shortcut for `TrailManager.setTrail` |
| `player.removeTrail()` | Shortcut for `TrailManager.removeTrail` |
| `player.hasTrail` | Property shortcut for `TrailManager.hasTrail` |

## Example

```kotlin
TrailManager.start(intervalTicks = 2)

player.setTrail(TrailConfig(
    particle = Particle.HEART,
    count = 3,
    offsetX = 0.2f,
    offsetY = 0.1f,
    offsetZ = 0.2f,
))

player.removeTrail()
```
