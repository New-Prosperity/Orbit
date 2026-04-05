# Nebula World Format (.nebula)

Custom binary world format for Orbit. Single-file, Zstd-compressed, palette-based. Replaces Anvil for game maps.

## Format

Magic `0x4E65624C` ("NebL"), version 1. Zstd compression. Palette-based block/biome storage per 16x16x16 section. Block entities with optional NBT. Light data with 4-state content (MISSING/EMPTY/FULL/PRESENT).

## Usage

### Load a world
```kotlin
val instance = NebulaWorldLoader.load("hub", Path.of("maps/hub.nebula"))
val (instance, future) = NebulaWorldLoader.loadAndPreload("hub", path, centerX, centerZ, radius)
```

### Convert Anvil → .nebula
```kotlin
NebulaWorldConverter.convert(Path.of("maps/hub"), Path.of("maps/hub.nebula"))
```

### Capture live instance → .nebula
```kotlin
val world = ReplayWorldCapture.capture(instance)
NebulaWorldWriter.write(world, Path.of("maps/build.nebula"))
```

### Read/write programmatically
```kotlin
val world = NebulaWorldReader.read(bytes)
val bytes = NebulaWorldWriter.write(world)
```

## Integration

`MapLoader.resolve()` checks for `.nebula` files first. `AnvilWorldLoader` delegates to `NebulaWorldLoader` when path ends with `.nebula`. Transparent to HubMode/GameMode.

## Classes

| Class | Purpose |
|-------|---------|
| `NebulaWorld` | In-memory world (chunks, sections, block entities, user data) |
| `NebulaWorldReader` | Binary → NebulaWorld (Zstd decompression, palette unpacking) |
| `NebulaWorldWriter` | NebulaWorld → binary (Zstd compression, palette packing) |
| `NebulaChunkLoader` | Minestom ChunkLoader bridge |
| `NebulaWorldLoader` | High-level load/preload/verify API |
| `NebulaWorldConverter` | Anvil directory → .nebula file (instance cleanup via try-finally) |
| `PaletteUtil` | Bit-packing pack/unpack for palette indices |
