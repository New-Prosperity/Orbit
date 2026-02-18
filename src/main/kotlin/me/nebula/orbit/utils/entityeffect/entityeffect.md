# Entity Effect

Temporary entity spawning with lifecycle callbacks and per-tick updates.

## Overview

Creates short-lived entities (armor stands, particles, etc.) with configurable lifecycle hooks. Entities auto-remove after specified duration or can be manually managed via callbacks.

## Key API

- `entityEffect(block: EntityEffectConfig.() -> Unit)` - Create and manage temporary entity with callbacks
  - `entityType: EntityType` - Entity type to spawn (default: ARMOR_STAND)
  - `durationTicks: Int` - Lifespan in ticks (default: 40)
  - `position: Pos` - Spawn position (default: ZERO)
  - `instance: Instance` - Instance to spawn in
  - `onSpawn: (Entity) -> Unit` - Called when entity spawns
  - `onTick: (Entity, Int) -> Unit` - Called each tick with entity and tick count
  - `onRemove: (Entity) -> Unit` - Called before entity removal
- `spawnTemporaryEntity(instance, type, pos, durationTicks): Entity` - Simple temporary entity

## Examples

```kotlin
entityEffect {
    entityType = EntityType.ARMOR_STAND
    instance = myInstance
    position = Pos(0.5, 64.0, 0.5)
    durationTicks = 80
    onSpawn = { entity -> entity.isInvisible = true }
    onTick = { entity, tick -> println("Tick: $tick") }
    onRemove = { entity -> println("Entity removed") }
}

val tempEntity = spawnTemporaryEntity(instance, EntityType.ITEM_FRAME, pos)
```
