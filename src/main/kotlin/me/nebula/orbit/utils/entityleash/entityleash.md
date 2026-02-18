# Entity Leash

Entity leash/chain system that enforces max distance between two entities via scheduled velocity correction.

## Key Classes

- **`EntityLeashManager`** -- object managing all active leashes
- **`LeashHandle`** -- handle returned on leash creation, call `release()` to remove

## Usage

### Leash an entity

```kotlin
val handle = EntityLeashManager.leash(zombie, player, maxDistance = 10.0)
```

### Release

```kotlin
handle.release()

EntityLeashManager.unleash(zombie)
```

### Query

```kotlin
val leashed = EntityLeashManager.isLeashed(zombie)
val holder = EntityLeashManager.holder(zombie)
```

### Extension functions

```kotlin
val handle = zombie.leashTo(player, maxDistance = 10.0)
zombie.unleash()
val leashed = zombie.isLeashed()
val holder = zombie.leashHolder()
```

## API

| Method | Description |
|--------|-------------|
| `leash(entity, holder, maxDistance)` | Create leash, returns `LeashHandle` |
| `unleash(entity)` | Remove leash from entity |
| `isLeashed(entity)` | Check if entity is leashed |
| `holder(entity)` | Get the holder entity, or null |
| `LeashHandle.release()` | Release this leash |
| `Entity.leashTo(holder, maxDistance)` | Extension: leash entity to holder |
| `Entity.unleash()` | Extension: remove leash |
| `Entity.isLeashed()` | Extension: check if leashed |
| `Entity.leashHolder()` | Extension: get holder entity |
