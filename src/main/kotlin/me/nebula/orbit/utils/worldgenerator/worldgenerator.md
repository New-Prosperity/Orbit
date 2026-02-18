# World Generator

Pre-built generators and a DSL for creating layered world generators.

## Key Classes

- **`LayeredGenerator`** -- `Generator` implementation with stacked block layers
- **`GeneratorLayer`** -- data class: `minY`, `maxY`, `block`
- **`LayeredGeneratorBuilder`** -- DSL builder

## Generators

### Layered (DSL)

```kotlin
val generator = layeredGenerator {
    bedrock()
    stone(1, 58)
    dirt(58, 62)
    grass(62)
    fill(63, 64, Block.SAND)
}
```

### Flat

```kotlin
val generator = flatGenerator(height = 40, surface = Block.GRASS_BLOCK)
```

Produces bedrock + stone + dirt + surface layer.

### Void

```kotlin
val generator = voidGenerator()
```

### Super Flat

```kotlin
val generator = superFlatGenerator(
    1 to Block.BEDROCK,
    3 to Block.DIRT,
    1 to Block.GRASS_BLOCK,
)
```

Stacks layers bottom-up by thickness.

### Checkerboard

```kotlin
val generator = checkerboardGenerator(
    height = 1,
    block1 = Block.WHITE_CONCRETE,
    block2 = Block.BLACK_CONCRETE,
    baseY = 40,
)
```

Stone base up to `baseY`, then alternating blocks on the surface.

## Builder Helpers

| Method | Description |
|--------|-------------|
| `bedrock(y)` | Single bedrock layer at Y |
| `stone(minY, maxY)` | Stone fill |
| `dirt(minY, maxY)` | Dirt fill |
| `grass(y)` | Single grass block layer |
| `fill(minY, maxY, block)` | Arbitrary block fill |
