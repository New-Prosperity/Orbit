# Custom 3D Armor

Shader-based 3D armor rendering using GLSL raycasting. Place `.bbmodel` files in `data/customcontent/armors/` — auto-detected at startup.

## Bone Naming Convention

Prefix bone names to map to armor parts (case-insensitive):

| Prefix | Part | Equip Slot |
|---|---|---|
| `h_` | Helmet | Helmet |
| `c_` | Chestplate | Chestplate |
| `ra_` | Right Arm | Chestplate |
| `la_` | Left Arm | Chestplate |
| `ia_` | Inner Armor (waist) | Leggings |
| `rl_` | Right Leg | Leggings |
| `ll_` | Left Leg | Leggings |
| `rb_` | Right Boot | Boots |
| `lb_` | Left Boot | Boots |

Bones without a recognized prefix are ignored. Nested prefixed sub-groups are supported (e.g., `rb_right_boot` inside `rl_right_leg`).

## Group Rotations

Non-prefixed sub-groups with rotation are fully supported. The parser uses multi-level rotation:
- Rotates element centers through each group's origin (innermost first) for correct positioning
- Stores each rotation level separately with its own pivot (group pivot or element pivot)
- Single-level rotations (element-only or group-only) use the `ADD_BOX_WITH_ROTATION_ROTATE` macro
- Multi-level rotations (element inside rotated group) generate inline GLSL that sequentially undoes each rotation around its own pivot

This correctly handles elements with rotation inside rotated sub-groups, where different pivots require sequential undo transforms.

## Texture Layer Splitting

Elements that reference a different texture than their bone's expected layer are auto-reassigned:
- Chestplate (layer 1) elements with texture 1 → InnerArmor (layer 2)
- Leg (layer 2) elements with texture 0 → Boot (layer 1)
- And vice versa

## Usage

### Auto-detection
Place `.bbmodel` files in `data/customcontent/armors/`. File name becomes the armor ID.

### Programmatic
```kotlin
val armor = CustomArmorRegistry["ranger_armor"]
armor?.equipFullSet(player)

val chestplate = armor?.createItem(ArmorPart.Chestplate)
player.inventory.addItemStack(chestplate)
```

### Test Commands
- `/armor list` — show all registered armors
- `/armor equip <armor_id>` — equip full set
- `/armor give <armor_id> <slot>` — give single piece (helmet, chestplate, right_arm, etc.)

## How It Works

1. Each armor gets a unique RGB color via `ModelIdRegistry`
2. Server equips leather armor dyed to that color
3. Vertex shader detects the color at pixel `(63,31)` of the leather texture
4. Fragment shader raycasts 3D cubes (from `ADD_BOX` macros) instead of rendering flat leather
5. Shader files: 16 static (from MC 1.21.6 overlay) + 2 generated (`armor.glsl`, `armorcords.glsl`)
