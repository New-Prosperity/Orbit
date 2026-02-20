# Custom Content

Custom item and block system. JSON config or code DSL definitions, Blockbench `.bbmodel` model source, resource pack merging.

## Directory Structure

```
data/customcontent/
├── items/          JSON item definitions
├── blocks/         JSON block definitions
├── models/         .bbmodel files
├── allocations.dat Block state allocations (persistent)
├── model_ids.dat   CustomModelData ID assignments (persistent)
└── pack.zip        Merged resource pack output
```

All directories are auto-created on startup.

## Item JSON

`data/customcontent/items/ruby_sword.json`:
```json
{
    "id": "ruby_sword",
    "base_material": "minecraft:diamond_sword",
    "display_name": "<red>Ruby Sword",
    "lore": ["<gray>Forged from rubies"],
    "unbreakable": true,
    "glowing": false,
    "max_stack_size": 1,
    "model": "ruby_sword.bbmodel"
}
```

## Block JSON

`data/customcontent/blocks/ruby_ore.json`:
```json
{
    "id": "ruby_ore",
    "hitbox": "full",
    "item": "ruby_ore",
    "hardness": 3.0,
    "place_sound": "block.stone.place",
    "break_sound": "block.stone.break",
    "model": "ruby_ore.bbmodel",
    "drops": {
        "self": false,
        "loot_table": {
            "rolls": 1,
            "entries": [
                { "material": "minecraft:diamond", "weight": 1, "min_count": 1, "max_count": 3 }
            ]
        }
    }
}
```

Self-dropping block (shorthand):
```json
{
    "id": "marble_slab",
    "hitbox": "slab",
    "item": "marble_slab",
    "hardness": 2.0,
    "model": "marble_slab.bbmodel",
    "drops": { "self": true }
}
```

## Code DSL

```kotlin
customItem("ruby_sword") {
    material(Material.DIAMOND_SWORD)
    name("<red>Ruby Sword")
    lore("<gray>Forged from rubies")
    unbreakable()
    model("ruby_sword.bbmodel")
}

customBlock("ruby_ore") {
    hitbox(BlockHitbox.Full)
    item("ruby_ore")
    hardness(3f)
    breakSound("block.stone.break")
    placeSound("block.stone.place")
    model("ruby_ore.bbmodel")
    drops {
        entry(Material.DIAMOND, minCount = 1, maxCount = 3)
    }
}
```

## Hitbox Types

| Type | Pool Source | Count | Blockstate Override |
|---|---|---|---|
| `full` | note_block (400) + mushroom blocks (192) | 592 | Yes — complete `variants` with vanilla fallback |
| `thin` | 17 carpet colors | 17 | Yes — single-variant replacement |
| `slab` | 53 slab materials (canonical `type=bottom`) | 53 | No — renders as vanilla material |
| `stair` | 50 stair materials (canonical state) | 50 | No — renders as vanilla material |
| `transparent` | tripwire boolean combos | 128 | No — renders as vanilla material |
| `wall` | 22 wall materials | 22 | No — renders as vanilla material |
| `fence` | 12 fence materials | 12 | No — renders as vanilla material |
| `trapdoor` | 11 trapdoor materials | 11 | No — renders as vanilla material |

**Blockstate override** means the placed block renders with the custom model. Types without blockstate override use the vanilla block appearance when placed (the custom model is only visible as the held item). This is because replacing blockstate files for complex blocks (slabs, stairs, fences, walls, trapdoors, tripwire) would break all vanilla instances of that material.

For `full` blocks, a complete `variants` blockstate is generated covering all 800 note_block states and all 64 mushroom block states per type, with non-allocated states falling back to the vanilla model.

## Block State Allocation

`BlockStateAllocator` assigns vanilla block states to custom blocks from deterministic pools. Allocations persist to `allocations.dat` ensuring placed blocks never change visual across restarts.

## Pack Merging

`PackMerger.merge()` collects:
1. ModelEngine bone models + textures
2. Custom item models (parsed from .bbmodel)
3. Custom block models (parsed from .bbmodel)
4. Item model overrides (CustomModelData -> model path)
5. Blockstate overrides (`full` and `thin` types only)

Output: `data/customcontent/pack.zip` with SHA-1 hash.

## Mechanic Guards

NoteBlockModule, BlockModule, SlabModule skip processing for blocks that are custom block allocated states.

## API

```kotlin
CustomItemRegistry[id]                      // get item by ID
CustomItemRegistry.byCustomModelData(cmd)   // get item by CMD
CustomBlockRegistry[id]                     // get block by ID
CustomBlockRegistry.fromVanillaBlock(block) // get block from world block
CustomBlockRegistry.fromItemId(itemId)      // get block from associated item ID
CustomContentRegistry.packBytes             // merged pack ZIP bytes
CustomContentRegistry.packSha1              // pack SHA-1 hash
```
