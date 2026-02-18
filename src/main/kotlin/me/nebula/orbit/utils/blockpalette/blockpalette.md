# Block Palette

DSL for weighted random block selection, useful for world generation and terrain manipulation.

## Key Classes

- **`BlockPalette`** -- named palette with weighted block entries
- **`WeightedBlock`** -- block with selection weight
- **`BlockPaletteBuilder`** -- DSL builder

## Usage

### Create

```kotlin
val terrain = blockPalette("terrain") {
    block(Block.GRASS_BLOCK, weight = 5.0)
    block(Block.DIRT, weight = 3.0)
    block(Block.COARSE_DIRT, weight = 1.0)
    block(Block.GRAVEL, weight = 0.5)
}
```

### Use

```kotlin
val block = terrain.randomBlock()

terrain.place(instance, pos)

terrain.fillRegion(instance, min, max)

instance.fillWithPalette(terrain, min, max)
```

## API

| Method | Description |
|--------|-------------|
| `randomBlock()` | Weighted random block from palette |
| `place(instance, pos)` | Place a random block at position |
| `fillRegion(instance, min, max)` | Fill cuboid region with random palette blocks |
| `Instance.fillWithPalette(palette, min, max)` | Extension: fill region using palette |
