# RayTrace

Step-based ray tracing for blocks and entities, plus bounding-box accurate `raycast` that finds both in a single pass.

## Basic Ray Tracing

### Block Ray Trace

```kotlin
val hit: BlockRayResult? = rayTraceBlock(instance, origin, direction, maxDistance = 10.0, step = 0.1)
hit?.let { println("${it.block} at ${it.position}, distance ${it.distance}") }
```

### Entity Ray Trace

```kotlin
val hit: EntityRayResult? = rayTraceEntity(instance, origin, direction, maxDistance = 5.0, step = 0.2, exclude = player, radius = 0.5)
hit?.let { println("${it.entity} at distance ${it.distance}") }
```

### Player Extensions (Basic)

```kotlin
val direction: Vec = player.lookDirection()
val blockHit: BlockRayResult? = player.rayTraceBlock(maxDistance = 10.0)
val entityHit: EntityRayResult? = player.rayTraceEntity(maxDistance = 5.0)
```

Both use eye position (y + 1.62) and exclude self for entity traces.

## Raycast (Bounding-Box)

Single-pass ray that checks entity bounding boxes and solid blocks simultaneously. Returns first hit of each type.

```kotlin
val result: RaycastResult = raycast(instance, origin, direction, maxDistance = 20.0, stepSize = 0.5)
result.hitEntity
result.hitBlock
result.hitPos
result.distance
```

### Player Extensions (Bounding-Box)

```kotlin
val result: RaycastResult = player.lookingAt(maxDistance = 5.0)

val entity: Entity? = player.getLookedAtEntity(maxDistance = 5.0) { entity ->
    entity is LivingEntity
}

val block: Point? = player.getLookedAtBlock(maxDistance = 5.0)
```

`getLookedAtEntity` supports an `entityFilter` predicate and returns the closest matching entity. `getLookedAtBlock` returns the block position of the first non-air, non-liquid block.

## Data Classes

| Class | Fields |
|---|---|
| `BlockRayResult` | `position: Vec`, `block: Block`, `distance: Double` |
| `EntityRayResult` | `entity: Entity`, `distance: Double` |
| `RaycastResult` | `hitEntity: Entity?`, `hitBlock: Point?`, `hitPos: Pos`, `distance: Double` |

## API Summary

| Function | Description |
|---|---|
| `rayTraceBlock(instance, origin, direction, maxDistance, step)` | First non-air block along ray |
| `rayTraceEntity(instance, origin, direction, maxDistance, step, exclude, radius)` | First entity within radius along ray |
| `raycast(instance, origin, direction, maxDistance, stepSize)` | Bounding-box entity + solid block in one pass |
| `Player.lookDirection()` | Unit vector from yaw/pitch |
| `Player.rayTraceBlock(maxDistance)` | Block trace from eye |
| `Player.rayTraceEntity(maxDistance)` | Entity trace from eye (excludes self) |
| `Player.lookingAt(maxDistance)` | Full raycast from eye |
| `Player.getLookedAtEntity(maxDistance, entityFilter)` | Closest entity in crosshair (bounding-box) |
| `Player.getLookedAtBlock(maxDistance)` | Block position in crosshair |
