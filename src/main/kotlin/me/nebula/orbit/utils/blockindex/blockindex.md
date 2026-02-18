# BlockPositionIndex

Per-instance spatial index that tracks positions of specific block types. Eliminates O(n^3) cubic scans by maintaining a set of known positions updated via block place/break events.

## Usage

```kotlin
val index = BlockPositionIndex(
    targetBlockNames = setOf("minecraft:beacon"),
    eventNode = eventNode
).install()

// Query nearby tracked blocks — O(k) where k = total tracked positions
val nearby = index.positionsNear(instance, player.position.asVec(), radius = 50.0)

// Seed from anvil-loaded chunks
index.scanChunk(instance, chunk)

// Cleanup on instance removal
index.instancePositions.cleanOnInstanceRemove { it }
```

## Coordinate Packing

Uses `(x << 40) | ((y & 0xFFFFF) << 20) | (z & 0xFFFFF)` — supports ±524K range on all axes.

## API

| Method | Description |
|---|---|
| `install()` | Registers PlaceEvent + BreakEvent listeners on the eventNode |
| `positionsNear(instance, center, radius)` | Returns `List<Vec>` of tracked positions within radius |
| `allPositions(instance)` | Raw packed position set for an instance |
| `scanChunk(instance, chunk)` | Seeds index from a loaded chunk's block palette |
| `evictInstance(instanceHash)` | Drops all positions for a destroyed instance |
| `clear()` | Full reset |
