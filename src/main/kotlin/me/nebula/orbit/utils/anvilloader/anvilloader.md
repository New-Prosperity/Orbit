# Anvil World Loader

Named Anvil world loading with validation, chunk preloading, and lifecycle management.

## Key Classes

- **`AnvilWorldLoader`** -- singleton managing named Anvil-loaded instances

## Top-Level Functions

- **`loadAnvilWorld(name, path)`** -- shorthand for `AnvilWorldLoader.load`

## Usage

```kotlin
val instance = AnvilWorldLoader.load("lobby", Path.of("/worlds/lobby"))

val instance = loadAnvilWorld("lobby", "/worlds/lobby")

val (instance, future) = AnvilWorldLoader.loadAndPreload(
    "arena", Path.of("/worlds/arena"),
    centerChunkX = 5, centerChunkZ = 10, radius = 6
)
future.join()
```

### Validation

`load()` validates the world structure before loading:
- Checks `<worldPath>/region/` directory exists
- Checks at least one `.mca` region file is present
- Logs the region file count

### Post-Load Verification

```kotlin
val hasBlocks = AnvilWorldLoader.verifyLoaded(instance, Pos(0.5, 65.0, 0.5))
```

Scans a column downward from the given position to confirm the loaded world contains solid blocks.

### Queries

```kotlin
AnvilWorldLoader.get("lobby")
AnvilWorldLoader.require("lobby")
AnvilWorldLoader.isLoaded("lobby")
AnvilWorldLoader.all()
AnvilWorldLoader.names()
```

### Unload

```kotlin
AnvilWorldLoader.unload("lobby")
AnvilWorldLoader.unloadAll()
```

## API

| Method | Description |
|--------|-------------|
| `validate(worldPath)` | Verify region directory and .mca files exist |
| `load(name, path)` | Validate + load Anvil world into a named `InstanceContainer` |
| `loadAndPreload(name, path, centerChunkX, centerChunkZ, radius)` | Load + preload chunks around center |
| `verifyLoaded(instance, checkPos)` | Verify blocks exist at a position column |
| `get(name)` | Nullable lookup |
| `require(name)` | Non-null lookup (throws if missing) |
| `unload(name)` | Kick players + unregister instance |
| `unloadAll()` | Unload all loaded worlds |

Duplicate names are rejected with `require`. Unloading kicks all players in the instance.

## World Directory Structure

The world folder must contain a `region/` subdirectory with `.mca` files:
```
worlds/hub/
└── region/
    ├── r.0.0.mca
    ├── r.-1.0.mca
    └── ...
```
