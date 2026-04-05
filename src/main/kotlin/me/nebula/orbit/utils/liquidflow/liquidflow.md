# liquidflow

Queue-based liquid flow simulation engine for Minestom instances. Handles level-based spreading, draining, falling, and infinite source creation.

## Usage

```kotlin
val engine = LiquidFlowEngine(
    instance = myInstance,
    sourceBlock = Block.WATER,
    maxLevel = 7,
    infiniteSource = true,
)

// Notify when a block changes near liquid
engine.notifyBlockChanged(x, y, z)

// Call every N ticks to process flow updates
engine.tick()
```

## API

| Method | Description |
|---|---|
| `scheduleUpdate(x, y, z)` | Queue a single position for flow evaluation |
| `scheduleNeighborUpdates(x, y, z)` | Queue all 6 neighbors of a position |
| `notifyBlockChanged(x, y, z)` | Queue position + all neighbors (use when a block is placed/removed) |
| `tick()` | Process up to 2048 queued positions per call |

## Flow Mechanics

- **Source blocks** (level 0): Permanent, placed by players via buckets
- **Flowing water** (level 1-7): Spreads outward, 1 level per flow tick. Level 7 is the furthest reach.
- **Falling water** (level 8): Created when liquid flows downward into air
- **Infinite source**: When a flowing block has 2+ adjacent horizontal source blocks, it becomes a source
- **Draining**: Flowing blocks with no valid feed path are removed

## Integration

The engine does not register event listeners. Wrap it in a `VanillaModule` (see `WaterFlowModule`) or call `notifyBlockChanged` from external systems (explosions, world edit, etc.) to trigger flow updates.

## Position Packing

Positions are packed into `Long` for efficient queue storage:
- `LiquidFlowEngine.packPos(x, y, z)` / `unpackX` / `unpackY` / `unpackZ`
- Same bit layout as vanilla block position encoding (38/26/12 bits for x/z/y)
- Unpack uses explicit mask + sign extension for correct negative coordinate handling
