# EntitySpawnerPool

Reusable entity pool to minimize GC from entity creation/destruction. Entities are recycled instead of discarded.

## Usage

```kotlin
val zombiePool = entityPool(EntityType.ZOMBIE, poolSize = 20) {
    onAcquire { entity ->
        entity.setInstance(gameInstance, spawnPos)
        entity.isGlowing = true
    }
    onRelease { entity ->
        entity.isGlowing = false
    }
}

val zombie = zombiePool.acquire()
// ... use the entity ...
zombiePool.release(zombie)
```

## API

- `entityPool(entityType, poolSize) { }` -- DSL to create and warmup an `EntityPool`
- `EntityPool.acquire(): Entity` -- get an entity from the pool (auto-expands if exhausted)
- `EntityPool.release(entity)` -- return entity to pool, removes from instance, resets health/velocity
- `EntityPool.releaseAll()` -- release all in-use entities
- `EntityPool.destroy()` -- release all and clear the pool
- `EntityPool.availableCount` / `inUseCount` / `totalCount` -- pool stats

## Behavior

- `warmup()` is called automatically on creation, pre-allocating `poolSize` entities
- On release: entity is removed from instance, health reset to max, velocity zeroed
- Pool auto-expands when exhausted (no hard cap)
- Uses `EntityCreature` when possible, falls back to `Entity`
