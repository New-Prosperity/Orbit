# Boundary

Invisible wall system that prevents players from leaving defined areas. Supports cuboid and circular shapes.

## BoundaryManager

| Method | Description |
|---|---|
| `start()` | Register global PlayerMoveEvent listener |
| `stop()` | Remove listener and clear all boundaries |
| `register(boundary)` | Register a boundary (auto-called by DSL) |
| `unregister(name)` | Remove a boundary by name |
| `get(name)` | Get boundary by name |
| `all()` | All registered boundaries |
| `clear()` | Remove all boundaries |

## BoundaryShape

Sealed class with two variants:
- `Cuboid(minX, maxX, minZ, maxZ)` -- axis-aligned rectangle
- `Circle(centerX, centerZ, radius)` -- circular area

## DSL

```kotlin
BoundaryManager.start()

boundary("spawn-area") {
    instance(instance)
    cuboid(-100.0, 100.0, -100.0, 100.0)
    minY(-64.0)
    maxY(320.0)
    onBlocked { player ->
        player.sendMM("<red>You cannot leave this area!")
    }
}

boundary("arena-ring") {
    instance(instance)
    circle(0.0, 0.0, 50.0)
    onBlocked { player -> }
}
```

## Cleanup

```kotlin
BoundaryManager.unregister("spawn-area")
BoundaryManager.stop()
```
