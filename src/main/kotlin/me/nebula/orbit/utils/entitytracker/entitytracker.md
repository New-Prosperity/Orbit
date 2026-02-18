# EntityTracker

Spatial query extensions for finding entities and players by distance, radius, or line of sight.

## Instance Extensions

| Function | Description |
|---|---|
| `instance.nearbyEntities(center, radius)` | All entities within radius of a point |
| `instance.nearbyPlayers(center, radius)` | All players within radius of a point |
| `instance.entitiesInLine(start, direction, length, width)` | Entities along a ray (default width 0.5) |

## Player Extensions

| Function | Description |
|---|---|
| `player.nearestPlayer(maxRadius)` | Closest player within radius (default 100) |
| `player.nearestEntity(maxRadius)` | Closest entity within radius (default 100) |

## Example

```kotlin
val nearby = instance.nearbyEntities(player.position, 10.0)

val closePlayers = instance.nearbyPlayers(Pos(0.0, 64.0, 0.0), 50.0)

val closest = player.nearestPlayer(maxRadius = 30.0)

val closestMob = player.nearestEntity(maxRadius = 20.0)

val inLine = instance.entitiesInLine(
    start = player.position,
    direction = player.position.direction(),
    length = 50.0,
    width = 1.0,
)
```
