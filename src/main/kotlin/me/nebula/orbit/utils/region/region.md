# Region

Sealed region types (cuboid, sphere, cylinder) with a global `RegionManager` registry and `RegionTracker` for enter/exit events.

## Key Classes

- **`Region`** (sealed) — base interface with `name` and `contains` checks
- **`CuboidRegion`** — axis-aligned bounding box
- **`SphereRegion`** — sphere defined by center + radius
- **`CylinderRegion`** — vertical cylinder defined by center + radius + height
- **`RegionManager`** — singleton registry with spatial queries
- **`RegionTracker`** — per-instance enter/exit event tracker (5-tick polling)

## Usage

```kotlin
val spawn = cuboidRegion("spawn", Pos(0.0, 60.0, 0.0), Pos(20.0, 80.0, 20.0))
val dome = sphereRegion("dome", Pos(100.0, 65.0, 100.0), radius = 50.0)
val tower = cylinderRegion("tower", Pos(0.0, 0.0, 0.0), radius = 10.0, height = 256.0)

RegionManager.register(spawn)
RegionManager.get("spawn")
RegionManager.require("spawn")
RegionManager.unregister("spawn")
RegionManager.all()

RegionManager.regionsAt(player.position)
RegionManager.isInAnyRegion(player.position)
RegionManager.playersInRegion(spawn, instance)
RegionManager.entitiesInRegion(spawn, instance)
```

## RegionTracker

Tracks player positions every 5 ticks and fires enter/exit handlers when players cross region boundaries.

### DSL

```kotlin
val tracker = regionTracker {
    track("spawn") {
        onEnter { player -> player.sendMessage("Entered spawn") }
        onExit { player -> player.sendMessage("Left spawn") }
    }
    track("arena") {
        onEnter { player -> player.setGameMode(GameMode.ADVENTURE) }
    }
}
tracker.install(instance)
```

### Manual API

```kotlin
val tracker = RegionTracker()
tracker.onEnter("spawn") { player -> ... }
tracker.onExit("spawn") { player -> ... }
tracker.install(instance)
```

| Method | Description |
|---|---|
| `onEnter(regionName, handler)` | Register enter handler for a region |
| `onExit(regionName, handler)` | Register exit handler for a region |
| `install(instance)` | Start polling on the instance scheduler (5 ticks) |
| `uninstall()` | Cancel the polling task |
| `destroy()` | Cancel task and clear all state |

Factory functions auto-normalize min/max for cuboid regions. Sphere uses squared-distance for efficient checks.
