# BlockSnapshot

Block capture and restore system. Captures block state from an instance region into packed Long-keyed storage, then restores, diffs, or pastes to arbitrary locations. Includes a `BlockRestoreHandle` DSL for tracked block modifications with auto-restore.

## BlockSnapshot

### Capture

```kotlin
val region = captureRegion(0, 60, 0, 50, 80, 50)
val snapshot = BlockSnapshot.capture(instance, region)

val snapshot2 = BlockSnapshot.capture(instance, minPoint, maxPoint)

val snapshot3 = BlockSnapshot.capture(instance, cuboidRegion)

val snapshot4 = instance.captureSnapshot(region)
val snapshot5 = instance.captureSnapshot(minPoint, maxPoint)
val snapshot6 = instance.captureSnapshot(sphereRegion)

val asyncSnapshot = BlockSnapshot.captureAsync(instance, region)
```

### Restore

```kotlin
snapshot.restore(instance)

snapshot.restoreAsync(instance)
```

### Diff

```kotlin
val changes: List<BlockChange> = snapshot.diff(instance)
changes.forEach { change ->
    println("${change.x},${change.y},${change.z}: ${change.before} -> ${change.after}")
}
```

### Paste at Offset

```kotlin
snapshot.pasteAt(instance, Pos(100.0, 64.0, 100.0))
```

### Create Instance

```kotlin
val newInstance: InstanceContainer = snapshot.createInstance()
```

### Properties

| Property | Type | Description |
|---|---|---|
| `blockCount` | `Int` | Number of stored blocks |
| `width` | `Int` | X dimension |
| `height` | `Int` | Y dimension |
| `depth` | `Int` | Z dimension |

### Methods

| Method | Description |
|---|---|
| `restore(instance)` | Set all captured blocks back into instance |
| `restoreAsync(instance)` | Async restore returning `CompletableFuture<Void>` |
| `diff(instance)` | List of `BlockChange` where current state differs from snapshot |
| `pasteAt(instance, offset)` | Paste snapshot shifted by offset point |
| `createInstance()` | Create a new `InstanceContainer` with snapshot blocks |
| `getBlock(x, y, z)` | Get a stored block or null |

### Companion Methods

| Method | Description |
|---|---|
| `capture(instance, CaptureRegion)` | Capture from a `CaptureRegion` |
| `capture(instance, min, max)` | Capture from two corner points |
| `capture(instance, Region)` | Capture from a `Region` (Cuboid/Sphere/Cylinder), skips air |
| `captureAsync(instance, CaptureRegion)` | Async capture returning `CompletableFuture` |

## CaptureRegion

```kotlin
val region = captureRegion(0, 60, 0, 50, 80, 50)
val region2 = captureRegion(minPos, maxPos)
```

| Property | Type |
|---|---|
| `minX/minY/minZ` | `Int` |
| `maxX/maxY/maxZ` | `Int` |
| `volume` | `Long` |

## BlockRestoreHandle

Tracks block modifications against an instance and restores originals on demand or after a timer.

```kotlin
val handle = instance.blockRestore {
    autoRestoreAfterSeconds(30)
}

handle.setBlock(pos1, Block.STONE)
handle.setBlock(pos2, Block.GOLD_BLOCK)

handle.modifiedCount
handle.isRestored

handle.restore()

handle.restoreAsync()
```

| Method | Description |
|---|---|
| `setBlock(pos, block)` | Place a block, recording the original |
| `restore()` | Restore all originals synchronously |
| `restoreAsync()` | Restore on a virtual thread |
| `isRestored` | Whether restore has been called |
| `modifiedCount` | Number of tracked modifications |

### Builder Options

| Method | Description |
|---|---|
| `autoRestoreAfter(Duration)` | Auto-restore after a `java.time.Duration` |
| `autoRestoreAfterSeconds(seconds)` | Auto-restore shortcut |
| `autoRestoreAfterTicks(ticks)` | Auto-restore shortcut (ticks * 50ms) |

### Top-level DSL

```kotlin
val handle = blockRestore(instance) {
    autoRestoreAfterTicks(200)
}
```

## Coordinate Packing

Blocks are stored in a `ConcurrentHashMap<Long, Block>` using packed Long coordinates: 26 bits for X (signed), 26 bits for Z (signed), 12 bits for Y (signed, -2048..2047). This minimizes memory overhead for large snapshots.
