# World Reset

Utilities for resetting chunks, clearing/filling block areas, removing entities, and recreating instances.

## Key Classes

- **`WorldReset`** -- singleton with world manipulation methods

## Instance Extensions

- **`InstanceContainer.resetChunks(minX, minZ, maxX, maxZ)`** -- unload and reload chunk range
- **`InstanceContainer.clearEntities(keepPlayers)`** -- remove all entities (optionally keep players)

## Usage

```kotlin
WorldReset.resetChunks(instance, minChunkX = -5, minChunkZ = -5, maxChunkX = 5, maxChunkZ = 5)

WorldReset.resetRadius(instance, centerX = 0, centerZ = 0, radius = 10)

WorldReset.clearArea(instance, Pos(0.0, 60.0, 0.0), Pos(50.0, 80.0, 50.0))

WorldReset.fillArea(instance, Pos(0.0, 63.0, 0.0), Pos(10.0, 63.0, 10.0), Block.STONE)

val newInstance = WorldReset.recreateInstance(instance, teleportTo = lobbyInstance, teleportPos = Pos(0.0, 64.0, 0.0))

WorldReset.removeEntities(instance, keepPlayers = true)
```

## API

| Method | Description |
|--------|-------------|
| `resetChunks(instance, ...)` | Unload + reload chunk range (regenerates from generator) |
| `resetRadius(instance, cx, cz, r)` | Reset chunks in square radius |
| `clearArea(instance, min, max)` | Set all blocks in area to AIR |
| `fillArea(instance, min, max, block)` | Fill area with a specific block |
| `recreateInstance(instance, ...)` | Destroy and recreate instance, preserving generator/chunk loader |
| `removeEntities(instance, keepPlayers)` | Remove all entities from instance |
