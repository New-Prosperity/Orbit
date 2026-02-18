# Region

Sealed region types (cuboid, sphere, cylinder) with a global `RegionManager` registry.

## Key Classes

- **`Region`** (sealed) — base interface with `name` and `contains` checks
- **`CuboidRegion`** — axis-aligned bounding box
- **`SphereRegion`** — sphere defined by center + radius
- **`CylinderRegion`** — vertical cylinder defined by center + radius + height
- **`RegionManager`** — singleton registry with spatial queries

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

Factory functions auto-normalize min/max for cuboid regions. Sphere uses squared-distance for efficient checks.
