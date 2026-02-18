# Schematic

Sponge schematic (.schem) loader and paster using `AbsoluteBlockBatch`.

## Key Classes

- **`Schematic`** — parsed schematic with `width`, `height`, `length`, and block data

## Usage

```kotlin
val schem = Schematic.load(Path.of("builds/arena.schem"))

schem.paste(instance, Pos(0, 65, 0))
schem.paste(instance, Pos(0, 65, 0), applyImmediately = false)

schem.width
schem.height
schem.length
schem.size
schem.getBlock(x, y, z)
```

## Extension Function

```kotlin
instance.pasteSchematic(schem, Pos(0, 65, 0))
```

## Details

- Loads from `Path` or `InputStream` (gzip-compressed NBT)
- Supports block states with properties (e.g., `minecraft:oak_stairs[facing=east]`)
- VarInt-encoded block data is decoded automatically
- Air blocks are skipped during paste for efficiency
