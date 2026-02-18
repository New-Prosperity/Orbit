# Chunk Loader

Async bulk chunk loading and unloading utilities for Minestom instances.

## Key Classes

- **`ChunkLoader`** -- singleton with static loading methods

## Instance Extensions

- **`Instance.loadChunkRadius(centerX, centerZ, radius)`** -- load chunks in a square radius
- **`Instance.preloadSpawnChunks(radius)`** -- preload around origin (default radius 4)

## Usage

```kotlin
ChunkLoader.loadRadius(instance, centerX = 0, centerZ = 0, radius = 8).thenAccept { count ->
    logger.info("Loaded $count chunks")
}

ChunkLoader.loadSquare(instance, minX = -5, minZ = -5, maxX = 5, maxZ = 5)

ChunkLoader.preloadAroundSpawn(instance, radius = 4)

instance.preloadSpawnChunks(radius = 6)

instance.loadChunkRadius(10, 10, 3)
```

## API

| Method | Description |
|--------|-------------|
| `loadRadius(instance, cx, cz, radius)` | Load square of chunks, returns `CompletableFuture<Int>` |
| `loadSquare(instance, minX, minZ, maxX, maxZ)` | Load rectangular chunk area |
| `loadChunks(instance, chunks)` | Load specific chunk coordinate pairs |
| `preloadAroundSpawn(instance, cx, cz, radius)` | Convenience for spawn area preloading |
| `unloadAll(instance)` | Unload all viewer-less chunks |
| `unloadOutsideRadius(instance, cx, cz, radius)` | Unload viewer-less chunks outside radius |
| `loadedChunkCount(instance)` | Current loaded chunk count |
