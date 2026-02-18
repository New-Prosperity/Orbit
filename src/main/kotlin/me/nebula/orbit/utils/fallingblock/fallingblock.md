# FallingBlock

Spawn falling block entities with gravity, optional velocity, and configurable landing behavior.

## Usage

```kotlin
spawnFallingBlock(instance, position, Block.SAND) { inst, pos, block ->
    inst.setBlock(pos, block)
}

launchBlock(instance, position, Block.TNT, direction = Vec(1.0, 1.0, 0.0), speed = 2.0)
```

## Key API

- `spawnFallingBlock(instance, position, block, velocity, onLand)` — spawn a falling block entity with gravity
- `launchBlock(instance, position, block, direction, speed, onLand)` — launch a block in a direction with normalized velocity
- `onLand` — callback `(Instance, Point, Block) -> Unit` when the entity lands (default: places the block)
- Entities auto-remove after 600 ticks (30s) if they never land
