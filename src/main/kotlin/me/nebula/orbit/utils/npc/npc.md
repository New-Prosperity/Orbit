# NPC

Packet-based fake NPCs with full skin support, any entity type, model-only mode, TextDisplay name tags, per-player visibility, and click interaction.

## Key Classes

- **`Npc`** -- packet-based fake entity with TextDisplay name, per-player visibility, click handler
- **`NpcVisual`** -- sealed interface: `SkinVisual` (player skin), `EntityVisual` (any entity type), `ModelVisual` (model-only, invisible INTERACTION hitbox)
- **`NpcBuilder`** -- DSL builder for NPC configuration
- **`NpcRegistry`** -- global registry tracking all NPCs, handles interaction events and look-at ticking

## Player NPC (default)

```kotlin
val shopkeeper = npc("<gold>Shopkeeper") {
    skin(PlayerSkin.fromUsername("Notch"))
    position(Pos(0.0, 65.0, 0.0))
    onClick { player -> player.sendMessage("Welcome!") }
    helmet(ItemStack.of(Material.DIAMOND_HELMET))
    mainHand(ItemStack.of(Material.DIAMOND_SWORD))
    lookAtPlayer(true)
}
```

## Entity NPC

Any `EntityType` with optional raw metadata:

```kotlin
val guard = npc("<red>Undead Guard") {
    entityType(EntityType.ZOMBIE)
    position(Pos(10.0, 65.0, 10.0))
    metadata(16, Metadata.Boolean(true)) // baby
    helmet(ItemStack.of(Material.IRON_HELMET))
    mainHand(ItemStack.of(Material.IRON_SWORD))
    onClick { player -> player.sendMessage("Grr!") }
    nameOffset(1.5)
}
```

## Model-Only NPC

No Minecraft entity visible — just Blockbench model bones. An invisible INTERACTION entity handles click detection.

```kotlin
val dragon = npc("<gold>Dragon Guardian") {
    modelOnly()
    position(Pos(100.0, 65.0, 100.0))
    lookAtPlayer(true)
    onClick { player -> player.sendMessage("Roar!") }
    nameOffset(3.0)
    model {
        model("dragon") { scale(2f) }
    }
}
```

## Model Attachment (any visual)

Any NPC type can have a model attached. The model renders alongside the entity visual:

```kotlin
val boss = npc("<dark_purple>Lich King") {
    entityType(EntityType.SKELETON)
    position(Pos(0.0, 65.0, 0.0))
    onClick { player -> player.sendMessage("You dare?") }
    model {
        model("lich_aura") { scale(1.5f) }
    }
}
```

## Builder API

| Method | Description |
|---|---|
| `skin(PlayerSkin)` / `skin(textures, signature)` | Set player skin (switches to `SkinVisual`) |
| `entityType(EntityType)` | Use any entity type (switches to `EntityVisual`) |
| `modelOnly()` | No entity, only model bones (switches to `ModelVisual`) |
| `position(Pos)` | NPC position |
| `onClick { player -> }` | Click handler |
| `lookAtPlayer(Boolean)` | Head tracking (default `true`) |
| `nameOffset(Double)` | TextDisplay Y offset above position (default `2.05`) |
| `metadata(index, entry)` | Raw entity metadata (for `EntityVisual`) |
| `helmet/chestplate/leggings/boots/mainHand/offHand(ItemStack)` | Equipment |
| `model { model("id") {} }` | Attach Blockbench model via `StandaloneModelOwner` |

## Instance Extension

```kotlin
val npc = instance.spawnNpc(Pos(0.0, 65.0, 0.0)) {
    skin(textures, signature)
    onClick { it.sendMessage("Hello!") }
}
```

## Extension Functions

```kotlin
player.showNpc(npc)
player.hideNpc(npc)
```

## Details

- **SkinVisual**: Spawns fake `EntityType.PLAYER` via `SpawnEntityPacket` + `PlayerInfoUpdatePacket` (for skin). `PlayerInfoRemovePacket` after 40 ticks. All skin parts enabled (0x7F).
- **EntityVisual**: Spawns any `EntityType` via `SpawnEntityPacket` + raw metadata. No PlayerInfo packets.
- **ModelVisual**: Spawns invisible `EntityType.INTERACTION` (1.0w x 2.0h) for click detection. No visible entity.
- Name displayed via a virtual `TextDisplay` entity positioned at `position + nameOffset`
- Per-player visibility tracked via `ConcurrentHashMap<UUID>`
- Interaction via `EntityAttackEvent` (left-click) and `PlayerEntityInteractEvent` (right-click) routed through `NpcRegistry`
- `lookAtPlayer` rotates head toward nearby players (within 10 blocks) every 5 ticks. For model NPCs, drives `headYaw`/`headPitch`.
- Entity IDs are negative (starting at -2,000,000) to avoid collision with real entities
- Equipment sent via `EntityEquipmentPacket` (works for player and humanoid entity types)
- All text supports MiniMessage format
