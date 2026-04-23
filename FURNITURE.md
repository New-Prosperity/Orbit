# Furniture

Custom-content subsystem for placing decorative, interactable, persistable 3D pieces into any `.nebula` world. Lives under `Orbit/src/main/kotlin/me/nebula/orbit/utils/customcontent/furniture/`.

- Placement is **atomic** (rollback if any cell fails validation).
- Rotation is **full 3D** (yaw + pitch + roll) via `ItemDisplay` quaternion.
- Collision is **per-cell smart** — barrier for full cells, borrowed invisible preset-shape blocks for sub-cell.
- Persistence round-trips through `.nebula` `userData` — no sidecar files.
- Integrates with the existing `CustomItem` / shop / loot-table / `Protection` infrastructure.

## Quick start

1. Author the model in Blockbench as a **Generic Model** (not Java Item).
2. Drop `oak_chair.bbmodel` into `resources/customcontent/furniture/`.
3. Optional: drop `oak_chair.json` alongside for interactions/footprint/sounds.
4. Boot the server (or run `/cc reload`) — the piece is registered.
5. `/cc furniture give oak_chair` → right-click a surface to place.

That's it. Restarts preserve everything.

## Blockbench project conventions

**Project type.** Use **Generic Model**. Java Item imposes an 8×8×8 bounding box and bakes in display-slot transforms we don't want — `ItemDisplay` handles both.

**Origin.** Put the model origin at the center of the anchor cell: `(0.5, 0, 0.5)` in block coordinates (pixel `(8, 0, 8)`). Rotation pivots around this point, so getting it right makes rotated pieces land correctly.

**Visual bones.** Structure your model hierarchy freely. All elements in bones *not* prefixed with `collider` render as the visible piece.

**Collider bones.** Bones whose name starts with `collider` declare the hitbox. Their elements are **stripped from the visual model** in the shipped pack (pack writer filters them out), so you can draw overlapping collision cubes without them showing up.

- `collider_*` (plain / legacy) → treated as `collider_solid`.
- `collider_solid_*` → emits a full-cube `Block.BARRIER` in each cell the bone's AABB touches.
- `collider_soft_*` → per-cell, infers a preset hitbox (Slab / Thin / Fence / Trapdoor / Full) from the AABB and places a borrowed invisible vanilla state with that collision shape.

You can **mix modes** in one piece. A chair could have `collider_soft_seat` (slim half-height seat — you can duck under the overhang) and `collider_solid_back` (full cube behind the sitter, no see-through). The everything-past-the-first-token suffix is freeform — `collider_soft_armrest_left` is fine.

**Solid wins at tie.** If both a `solid` and a `soft` bone cover the same cell, the cell becomes a barrier. This prevents accidental "I can walk through my table."

**Footprint comes for free.** When you use `"footprint": { "type": "bones", "prefix": "collider" }` in your JSON, the parser derives the integer cell grid from your collider AABBs. No manual cell lists.

## Sidecar JSON reference

Copy-paste templates for the common shapes live in `Orbit/samples/furniture/` — chair (seat), dining table (box footprint), cabinet (open/close + light-on-open), lamp (light level), painting (nonsolid). Start from one, rename to your id, drop next to your `.bbmodel`.

Drop `{id}.json` next to `{id}.bbmodel`. Bare `.bbmodel` with no JSON → registered as a single-cell, no-interaction piece using sane defaults.

```jsonc
{
  "id": "oak_chair",                   // registry key. Defaults to filename without .json
  "item": "oak_chair",                 // CustomItem id. Defaults to the furniture id
  "placeSound": "block.wood.place",
  "breakSound": "block.wood.break",
  "scale": 1.0,                        // uniform ItemDisplay scale
  "visualRotationSnap": 0.0,           // 0 = continuous yaw, or 22.5 / 45 / 90 / ...
  "collision": "solid",                // "solid" (default) or "nonsolid"
  "lightLevel": 0,                     // 0..15
  "colliderPrefix": "collider",        // override if your bones use a different prefix

  "placement": "floor",                // "floor" | "ceiling" | "wall" | "any" — or object (see below)
  // or: { "allowed_faces": ["top", "bottom"], "auto_orient": true }

  "footprint": { "type": "bones", "prefix": "collider" },
  // or: [[0,0,0], [1,0,0]]
  // or: { "type": "box", "size": [2, 1, 1] }
  // or: { "type": "cells", "cells": [[0,0,0], [1,0,0]] }

  "interaction": {
    "type": "seat",                    // "seat" | "open_close" | "loot_container" | "custom"
    "offsetY": 0.4,                    // seat: sitter Y offset from anchor
    "yawOffsetDegrees": 0              // seat: yaw delta relative to piece yaw
  }
}
```

### Placement profiles

Controls which block faces a furniture can be placed on, and whether to auto-orient the model to the clicked face:

| Profile | Allowed faces | `auto_orient` |
| --- | --- | --- |
| `floor` (default) | TOP only | false — piece stays upright, yaw follows player |
| `ceiling` | TOP, BOTTOM | true — on BOTTOM the piece hangs upside-down (180° pitch) |
| `wall` | NORTH, SOUTH, EAST, WEST | true — piece tilts to lie against the wall |
| `any` | all six | true — every face gets auto-oriented |

Object form lets you mix and match:
```json
"placement": { "allowed_faces": ["top", "bottom"], "auto_orient": true }
```

Clicking a disallowed face cancels the placement and sends the player a red message.

**Auto-orient math:** the model's default +Y axis is rotated to align with the clicked face's outward normal. `TOP`→identity. `BOTTOM`→pitch 180°. `NORTH`→pitch -90°. `SOUTH`→pitch +90°. `EAST`→roll -90°. `WEST`→roll +90°. Applied as a `placementRotation` on the model's root bones; animations compose on top via the same pipeline.

### Interaction variants

**`seat`**
```json
"interaction": { "type": "seat", "offsetY": 0.4, "yawOffsetDegrees": 0 }
```
Right-click → spawns a marker armor stand → player mounts. Sneak to dismount. Zero ambient entities when nobody's sitting (lazy spawn).

**`open_close`**
```json
"interaction": {
  "type": "open_close",
  "openItemId": "oak_cabinet_open",
  "closedItemId": "oak_cabinet_closed"
}
```
Right-click toggles between two `CustomItem` models on the `ItemDisplay`. State persists through save/load. Integrates with `lightOnlyWhenOpen` for lamps.

**`loot_container`**
```json
"interaction": {
  "type": "loot_container",
  "rows": 3,
  "titleKey": "orbit.furniture.oak_cabinet.title"
}
```
Right-click opens an `Inventory` (1–6 rows). Contents persist through save/load via NBT in the manifest. First player to open triggers hydration from persisted NBT.

**`custom`**
```json
"interaction": { "type": "custom", "handlerId": "nebula:vendor_talk" }
```
Dispatches to a handler registered via `FurnitureInteractionRegistry.register(id) { player, furniture -> ... }`. Use for module-specific behavior (NPC dialogs, quest triggers, etc.).

### NonSolid (rugs, paintings, wall decor)

```json
{ "collision": "nonsolid" }
```
No barriers. Interaction entities are spawned at each footprint cell for click targets. Players walk through — ideal for flat decorations.

## DSL reference

Code-first definition for programmatic pieces:

```kotlin
furniture("oak_chair") {
    item("oak_chair")
    placeSound("block.wood.place")
    scale(0.95)
    rotationSnap(0.0)
    collision(FurnitureCollision.Solid)
    seat(offsetY = 0.4)
    footprint {
        // cell(dx, dy, dz) — explicit
        // box(w, h, d)     — shorthand
        // cells(list)      — from external source
    }
}
```

`fromBones(...)` is JSON-only — the DSL doesn't have a bbmodel handle.

## Staff commands

| Command | What it does |
| --- | --- |
| `/cc furniture give <id> [amount]` | Put furniture item in hand. Tab-completes `id`. |
| `/cc furniture list` | Registered furniture summary (footprint, interaction, collision, light). |
| `/cc furniture placed` | Placed instances in current instance with owner info. |
| `/cc furniture select` | Raycast from crosshair, select the furniture under aim. |
| `/cc furniture rotate <axis> <degrees>` | Rotate the selected piece. Axis: `yaw`/`pitch`/`roll`. |
| `/cc furniture delete` | Delete the selected (or crosshair-aimed) piece. |
| `/cc furniture orphans` | Scan for stored-without-barrier + barrier-without-store orphans. |
| `/cc furniture repair` | Auto-fix orphans found by the scan. |

Permission: `orbit.furniture`.

## Persistence

**How it works.** `NebulaWorld.userData` carries a tagged JSON manifest. Format magic `"nebula-furniture"`, version 2. One entry per placed piece:

```json
{
  "uuid": "…",
  "definitionId": "oak_chair",
  "anchorX": 10, "anchorY": 64, "anchorZ": 20,
  "yawDegrees": 37.3, "pitchDegrees": 0, "rollDegrees": 0,
  "openCloseOpen": false,
  "inventoryBase64": null,
  "owner": "<uuid of placer>"
}
```

**When it saves.** `BuildMode` auto-saves every 5 minutes (only if dirty), on `/save`, and on shutdown. `BuildMode.save` is atomic (write to `.tmp` → rotate 3-deep backups → atomic rename).

**When it restores.** Every `.nebula` load through `NebulaWorldLoader.load()` fires the post-load hook registered by `Furniture.install()`. Pieces are re-registered in `PlacedFurnitureStore`, displays + interaction entities re-spawn, open states restore, inventories hydrate on first open.

**What's NOT persisted.** `cellKeys`, `displayEntityId`, `interactionEntityIds`, per-cell decisions — all derived at restore time from the `FurnitureDefinition` + rotation. Barriers are part of chunk data (already in `.nebula`) and come back via the chunk loader.

## Performance

- `PlacedFurnitureStore` is chunk-indexed — O(1) lookup by cell, by UUID, by chunk.
- `DisplayCullController` despawns `ItemDisplay` entities beyond 48 blocks from any player; respawns on approach. Barriers stay. Render count stays ≈ "near any player" regardless of total placed.
- Auto-save only fires when dirty (block/furniture mutation).
- Seats spawn a marker armor stand only while someone's actively sitting.
- Animations use client-side interpolation via `DisplayAnimator` keyframes — server sends ~2 kf/sec for smooth motion, client lerps the rest.

## Integration touchpoints

- **Shop / marketplace** — furniture items are `CustomItem`s tagged with `ITEM_ID_TAG`; they ride the existing purchase infrastructure.
- **Loot tables** — `LootTierBuilder.furniture(id, amount, weight, maxPerChest)` drops furniture directly into `ChestLootTable` tiers.
- **Protection** — `ProtectionManager` vetoes place/break through `PlayerBlockInteractEvent` / `PlayerBlockBreakEvent` cancellation; furniture listener respects `event.isCancelled`.
- **Economy perks** — furniture buy-paths use `EconomyPerks.costAfterShopDiscount` like any other shop item.
- **Translations** — all player-visible strings go through `TranslationKey`. Add keys under `orbit.furniture.*` in `Pulsar/src/main/resources/en.json` and republish Gravity.

## Events

Cancel or react to furniture lifecycle via `Event`:

- `FurniturePlacePreEvent(player, definition, anchor)` — cancelable. Fires before any state change.
- `FurniturePlacedEvent(player, furniture)` — fires after successful placement.
- `FurnitureBreakPreEvent(player?, furniture)` — cancelable.
- `FurnitureBrokenEvent(player?, furniture)` — fires after break.

## File layout

```
Orbit/src/main/kotlin/me/nebula/orbit/utils/customcontent/furniture/
├── Furniture.kt                    # facade + DSL
├── FurnitureDefinition.kt          # data class + enums
├── FurnitureInstance.kt            # runtime instance + cell keys
├── FurnitureFootprint.kt           # cell list
├── FurnitureInteraction.kt         # sealed: Seat | OpenClose | LootContainer | Custom
├── FurnitureInteractionRegistry.kt # Custom handler registry
├── FurnitureInteractionDispatcher.kt # routes clicks to the right controller
├── FurnitureRegistry.kt            # id → definition
├── PlacedFurnitureStore.kt         # per-instance spatial index
├── FurnitureInstanceState.kt       # per-UUID runtime state (open, inventory)
├── FurnitureListener.kt            # PlayerBlockInteractEvent + break handlers
├── FurnitureDisplaySpawner.kt      # ItemDisplay spawn/despawn + rotation
├── InteractionEntitySpawner.kt     # NonSolid click targets
├── FurnitureEvents.kt              # pre/post event types
├── FurniturePersistence.kt         # manifest v2 encode/decode
├── FurnitureJsonLoader.kt          # sidecar .json parser
├── BlockbenchColliderParser.kt     # bone classifier + cell decisions
├── HitboxInferrer.kt               # AABB → BlockHitbox preset
├── FurnitureAabb.kt                # clip/union/cellsTouched
├── CellDecision.kt                 # Barrier | Shaped(hitbox)
├── FurnitureCollisionStates.kt     # shared borrowed-state cache
├── FurnitureCollisionPack.kt       # empty-model + blockstate redirects
├── FurnitureLightingController.kt  # minecraft:light block per cell
├── SeatController.kt               # lazy marker armor stand mount
├── OpenCloseController.kt          # variant swap
├── LootContainerController.kt      # per-instance Inventory
├── DisplayAnimator.kt              # keyframe animation
├── DisplayCullController.kt        # proximity-based display culling
├── FurnitureOrphanReconciler.kt    # scan + repair
└── FootprintRotation.kt            # quarter-turn rotation math
```
