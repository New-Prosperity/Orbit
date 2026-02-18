# StructureBlock

Capture, store, paste, and rotate block structures within instances.

## Usage

```kotlin
val structure = captureStructure("tower", instance, posA, posB)
StructureRegistry.register(structure)

structure.paste(instance, origin)
structure.pasteRotated90(instance, origin, rotations = 2)
structure.clear(instance, origin)
```

## Key API

- `captureStructure(name, instance, from, to)` — capture all blocks in a region as a `Structure`
- `Structure.paste(instance, origin, ignoreAir)` — place the structure at an origin point
- `Structure.pasteRotated90(instance, origin, rotations, ignoreAir)` — paste with 90-degree rotations
- `Structure.clear(instance, origin)` — replace all structure positions with air
- `Structure.sizeX / sizeY / sizeZ` — bounding dimensions
- `StructureRegistry.register(structure)` — store a structure by name
- `StructureRegistry.get(name)` — retrieve a stored structure
- `StructureRegistry.remove(name)` — remove a stored structure
- `StructureRegistry.all()` — all registered structures
