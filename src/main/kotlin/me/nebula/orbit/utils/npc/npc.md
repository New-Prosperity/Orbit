# NPC

Packet-based fake player NPCs with full skin support, TextDisplay name tags, per-player visibility, and click interaction.

## Key Classes

- **`Npc`** -- packet-based fake player entity with TextDisplay name, skin support, equipment, per-player visibility
- **`NpcBuilder`** -- DSL builder for NPC configuration
- **`NpcRegistry`** -- global registry tracking all NPCs, handles interaction events and look-at ticking

## Usage

```kotlin
val shopkeeper = npc("<gold>Shopkeeper") {
    skin(PlayerSkin.fromUsername("Notch"))
    position(Pos(0.0, 65.0, 0.0))
    onClick { player -> player.sendMessage("Welcome!") }
    helmet(ItemStack.of(Material.DIAMOND_HELMET))
    chestplate(ItemStack.of(Material.DIAMOND_CHESTPLATE))
    mainHand(ItemStack.of(Material.DIAMOND_SWORD))
    lookAtPlayer(true)
}

player.showNpc(shopkeeper)
player.hideNpc(shopkeeper)
shopkeeper.remove()
```

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

- Spawns a fake `EntityType.PLAYER` via `SpawnEntityPacket` + `PlayerInfoUpdatePacket` (for skin)
- Name displayed via a virtual `TextDisplay` entity positioned above the player head (Y+2.05)
- `PlayerInfoRemovePacket` sent after 40 ticks to remove from tab list while keeping skin rendered
- All skin parts enabled (0x7F flag byte)
- Per-player visibility tracked via `ConcurrentHashMap<UUID>`
- Interaction via `EntityAttackEvent` (left-click) and `PlayerEntityInteractEvent` (right-click) routed through `NpcRegistry`
- `lookAtPlayer` rotates head toward nearby players (within 10 blocks) every 5 ticks
- Entity IDs are negative (starting at -2,000,000) to avoid collision with real entities
- Equipment sent via `EntityEquipmentPacket`
- All text supports MiniMessage format
