# WorldEdit

WorldEdit-like block operations with copy, paste, rotate, flip, fill, replace, pattern fill, undo, and redo.

## Key Classes

- **`WorldEdit`** -- singleton with all operations
- **`ClipboardData`** -- immutable block storage with packed Long coordinates
- **`Axis`** -- enum for flip direction (`X`, `Y`, `Z`)
- **`WeightedBlock`** -- block with a weight for pattern fills
- **`PatternBuilder`** -- weighted random block selection builder

## Usage

```kotlin
val clipboard = WorldEdit.copy(instance, region)
WorldEdit.paste(clipboard, instance, Pos(10.0, 65.0, 10.0), player)

val rotated = WorldEdit.rotate(clipboard, 90)
val flipped = WorldEdit.flip(clipboard, Axis.Y)

WorldEdit.fill(instance, region, Block.STONE, player)
WorldEdit.replace(instance, region, Block.DIRT, Block.GRASS_BLOCK, player)

WorldEdit.undo(player)
WorldEdit.redo(player)
WorldEdit.clearHistory(player)
```

## Extension Functions

```kotlin
val clipboard = instance.copyRegion(region)
instance.fillRegion(region, Block.STONE, player)
instance.replaceRegion(region, Block.DIRT, Block.GRASS_BLOCK, player)
player.undoEdit()
player.redoEdit()
```

## Pattern Fill

Weighted random block fill across a region. Each block is independently selected from the pattern.

### Direct API

```kotlin
val pattern = PatternBuilder().apply {
    random(Block.STONE, 0.5)
    random(Block.COBBLESTONE, 0.3)
    random(Block.ANDESITE, 0.2)
}
val blocksChanged: Int = WorldEdit.fillPattern(instance, region, pattern, player)
```

### Extension DSL

```kotlin
instance.fillPattern(region, player) {
    random(Block.STONE, 0.5)
    random(Block.COBBLESTONE, 0.3)
    random(Block.ANDESITE, 0.2)
}
```

Equal-weight shorthand:

```kotlin
instance.fillPattern(region, player) {
    random(Block.STONE, Block.COBBLESTONE, Block.ANDESITE)
}
```

### PatternBuilder API

| Method | Description |
|---|---|
| `random(vararg blocks)` | Add blocks with equal weight (1/n each) |
| `random(block, weight)` | Add a single block with explicit weight |

Weights are relative. `0.5 / 0.3 / 0.2` produces 50%/30%/20% distribution.

## Details

- Coordinates packed into Long (21 bits per axis, supports +/- 1M range)
- Works with all Region types (Cuboid, Sphere, Cylinder) from `utils/region`
- Undo/redo stacks per player via ConcurrentLinkedDeque, max 50 entries
- Paste optionally records undo when player provided
- Rotation supports 90/180/270 degrees (Y-axis rotation, swaps X and Z)
- Flip mirrors across the specified axis
- Fill/replace/fillPattern iterate bounding box and check region containment per block
