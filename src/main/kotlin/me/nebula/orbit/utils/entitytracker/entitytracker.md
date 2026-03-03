# EntityTracker

Spatial query extensions for finding nearest entities or entities along a line. For radius queries, use Minestom's built-in `Instance.getNearbyEntities(point, radius)`.

## Instance Extensions

| Function | Description |
|---|---|
| `instance.entitiesInLine(start, direction, length, width)` | Entities along a ray (default width 0.5) |

## Player Extensions

| Function | Description |
|---|---|
| `player.nearestPlayer(maxRadius)` | Closest player within radius (default 100) |
| `player.nearestEntity(maxRadius)` | Closest entity within radius (default 100) |

## Example

```kotlin
val nearby = instance.getNearbyEntities(player.position, 10.0)

val closest = player.nearestPlayer(maxRadius = 30.0)

val closestMob = player.nearestEntity(maxRadius = 20.0)

val inLine = instance.entitiesInLine(
    start = player.position,
    direction = player.position.direction(),
    length = 50.0,
    width = 1.0,
)
```
