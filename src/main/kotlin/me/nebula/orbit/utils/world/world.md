# World

Named instance (world) management via `WorldManager` singleton and DSL builder.

## Key Classes

- **`WorldManager`** — singleton registry for named `InstanceContainer` worlds
- **`WorldBuilder`** — DSL builder for world configuration

## Usage

```kotlin
val lobby = WorldManager.create("lobby") {
    flat(height = 40, material = Block.GRASS_BLOCK)
    spawn(Pos(0.0, 41.0, 0.0))
}

val arena = WorldManager.createVoid("arena")
val parkour = WorldManager.createFlat("parkour", height = 10, material = Block.STONE)

WorldManager.get("lobby")
WorldManager.require("lobby")
WorldManager.delete("arena")
WorldManager.all()
WorldManager.names()
```

## Builder Functions

| Function | Description |
|----------|-------------|
| `generator { unit -> ... }` | Custom chunk generator |
| `spawn(pos)` | Set spawn point |
| `void()` | No generation (void world) |
| `flat(height, material)` | Flat world filled to height |
